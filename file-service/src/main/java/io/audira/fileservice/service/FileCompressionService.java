package io.audira.fileservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Servicio encargado de la lógica de negocio para la compresión de archivos.
 * <p>
 * Utiliza las librerías nativas de {@code java.util.zip} para generar archivos .zip
 * gestionando streams de entrada y salida para optimizar el uso de memoria.
 * </p>
 */
@Service
public class FileCompressionService {

    /**
     * Directorio base inyectado desde la configuración donde se alojan los archivos.
     */
    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

/**
     * Comprime una lista de archivos existentes en un único archivo ZIP.
     * <p>
     * Crea un directorio "compressed" si no existe y genera un nombre de archivo aleatorio (UUID).
     * </p>
     *
     * @param filePaths Lista de rutas relativas de los archivos a comprimir (ej: "audio-files/cancion.mp3").
     * @return La ruta relativa del archivo ZIP generado.
     * @throws IOException Si ocurre un error de lectura/escritura o si no se puede crear el directorio.
     * @throws FileNotFoundException Si alguno de los archivos solicitados no existe en disco.
     */    
    public String compressFiles(List<String> filePaths) throws IOException {
        Path fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        // Crear directorio para archivos comprimidos
        Path compressedDir = fileStorageLocation.resolve("compressed");
        Files.createDirectories(compressedDir);

        // Generar nombre único para el archivo ZIP
        String zipFileName = UUID.randomUUID().toString() + ".zip";
        Path zipFilePath = compressedDir.resolve(zipFileName);

        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (String filePath : filePaths) {
                Path sourceFile = fileStorageLocation.resolve(filePath).normalize();

                if (!Files.exists(sourceFile)) {
                    throw new FileNotFoundException("Archivo no encontrado: " + filePath);
                }

                // Agregar archivo al ZIP
                addFileToZip(sourceFile, zos, sourceFile.getFileName().toString());
            }
        }

        return "compressed/" + zipFileName;
    }

/**
     * Comprime un solo archivo individualmente.
     * <p>
     * Útil para reducir el tamaño de transferencia de archivos pesados (como WAV o FLAC).
     * El nombre resultante conserva el original seguido de un sufijo UUID parcial.
     * </p> 
     * @param filePath Ruta relativa del archivo origen.
     * @return La ruta relativa del ZIP generado.
     * @throws IOException Si falla la operación de E/S o si hay un intento de violación de seguridad.
     */
    public String compressSingleFile(String filePath) throws IOException {
        /* 1. Definir el directorio base seguro */
        Path fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        /* 2. Resolver y normalizar la ruta solicitada */
        Path sourceFile = fileStorageLocation.resolve(filePath).normalize();

        /* 3. VALIDACIÓN DE SEGURIDAD (CRÍTICO) */
        /* Verificamos que la ruta resuelta comience con la ruta base. */
        /* Si no coincide, significa que el usuario intentó un Path Traversal (ej: ../../etc/passwd) */
        if (!sourceFile.startsWith(fileStorageLocation)) {
            throw new SecurityException("Acceso denegado: El archivo está fuera del directorio permitido.");
        }

        if (!Files.exists(sourceFile)) {
            throw new FileNotFoundException("Archivo no encontrado: " + filePath);
        }

        /* Crear directorio para archivos comprimidos */
        Path compressedDir = fileStorageLocation.resolve("compressed");
        Files.createDirectories(compressedDir);

        /* Generar nombre único para el archivo ZIP */
        String originalFileName = sourceFile.getFileName().toString();
        String zipFileName = originalFileName.substring(0, originalFileName.lastIndexOf('.')) + "_" +
                           UUID.randomUUID().toString().substring(0, 8) + ".zip";
        
        Path zipFilePath = compressedDir.resolve(zipFileName);

        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            addFileToZip(sourceFile, zos, originalFileName);
        }

        return "compressed/" + zipFileName;
    }

    /**
     * Método auxiliar interno para escribir los bytes de un archivo en el flujo ZIP.
     */
    private void addFileToZip(Path sourceFile, ZipOutputStream zos, String entryName) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName);
        zos.putNextEntry(zipEntry);

        /* CORRECCIÓN: Usar Files.newInputStream en lugar de new FileInputStream(File) */
        /* Al llegar aquí, sourceFile ya ha sido validado en el método anterior */
        try (InputStream fis = Files.newInputStream(sourceFile)) {
            
            byte[] buffer = new byte[1024];
            int length;

            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }

        zos.closeEntry();
    }

    /**
     * Método auxiliar interno para escribir los bytes de un archivo en el flujo ZIP.
     * <p>
     * Utiliza un buffer de 1KB para la transferencia de datos.
     * </p>
     *
     * @param sourceFile Ruta absoluta del archivo fuente en disco.
     * @param zos Flujo de salida del archivo ZIP.
     * @param entryName Nombre con el que se guardará el archivo dentro del ZIP.
     * @throws IOException Si falla la lectura o escritura.
     */
    private void addFileToZip(Path sourceFile, ZipOutputStream zos, String entryName) throws IOException {
        ZipEntry zipEntry = new ZipEntry(entryName);
        zos.putNextEntry(zipEntry);

        try (FileInputStream fis = new FileInputStream(sourceFile.toFile())) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }

        zos.closeEntry();
    }

    /**
     * Obtiene el tamaño físico de un archivo almacenado.
     *
     * @param filePath Ruta relativa del archivo.
     * @return Tamaño en bytes.
     * @throws IOException Si el archivo no existe o no se puede acceder a sus atributos.
     */
    public long getFileSize(String filePath) throws IOException {
        Path fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = fileStorageLocation.resolve(filePath).normalize();

        if (!Files.exists(file)) {
            throw new FileNotFoundException("Archivo no encontrado: " + filePath);
        }

        return Files.size(file);
    }
}
