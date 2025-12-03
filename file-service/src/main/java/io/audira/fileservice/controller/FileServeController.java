package io.audira.fileservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Controlador REST encargado de la distribución y descarga de archivos.
 * <p>
 * Su característica más importante es el soporte para <b>Range Requests</b> (RFC 7233).
 * Esto permite a los clientes solicitar solo fragmentos de un archivo de audio,
 * habilitando la funcionalidad de "seeking" (adelantar/retroceder) sin descargar el archivo entero.
 * </p>
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileServeController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

/**
     * Sirve un archivo específico almacenado en el sistema.
     * <p>
     * Determina dinámicamente si debe devolver el archivo completo (200 OK)
     * o solo un fragmento (206 Partial Content) basándose en la cabecera {@code Range}
     * y si el archivo es de tipo audio.
     * </p>
     *
     * @param subDirectory Subdirectorio de categoría (ej: "audio-files", "images").
     * @param fileName Nombre del archivo con su extensión.
     * @param rangeHeader (Opcional) Cabecera HTTP {@code Range} indicando los bytes requeridos.
     * @return Un {@link ResponseEntity} que contiene el recurso o un fragmento del mismo.
     */    
    @GetMapping("/{subDirectory}/{fileName:.+}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String subDirectory,
            @PathVariable String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        try {
            Path fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = fileStorageLocation.resolve(subDirectory).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                String contentType = determineContentType(fileName);
                long fileSize = Files.size(filePath);

                // Soporte para streaming de audio con Range requests
                if (rangeHeader != null && isAudioFile(fileName)) {
                    return handleRangeRequest(resource, rangeHeader, fileSize, contentType, fileName);
                }

                // Respuesta normal para archivos sin Range request
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .contentLength(fileSize)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

/**
     * Determina el tipo MIME (Content-Type) basado en la extensión del archivo.
     *
     * @param fileName Nombre del archivo.
     * @return El tipo MIME detectado (ej: "audio/mpeg") o "application/octet-stream" por defecto.
     */    
    private String determineContentType(String fileName) {
        String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (fileExtension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "flac":
                return "audio/flac";
            case "midi":
            case "mid":
                return "audio/midi";
            default:
                return "application/octet-stream";
        }
    }

/**
     * Verifica si el archivo solicitado es un audio compatible.
     *
     * @param fileName Nombre del archivo a validar.
     * @return {@code true} si la extensión es mp3, wav, flac, midi o mid.
     */    
    private boolean isAudioFile(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return extension.equals("mp3") || extension.equals("wav") ||
               extension.equals("flac") || extension.equals("midi") || extension.equals("mid");
    }

    /**
     * Maneja la lógica de las peticiones parciales (Streaming).
     * <p>
     * Calcula el inicio y fin del rango de bytes solicitado y configura la respuesta
     * HTTP 206 con los headers {@code Content-Range} y {@code Content-Length} adecuados.
     * </p>
     *
     * @param resource El recurso físico del archivo.
     * @param rangeHeader El valor crudo del header Range (ej: "bytes=0-1024").
     * @param fileSize Tamaño total del archivo en bytes.
     * @param contentType Tipo MIME del archivo.
     * @param fileName Nombre del archivo para el header Content-Disposition.
     * @return Respuesta HTTP 206 (Partial Content) o 200 (OK) en caso de error de parsing.
     * @throws IOException Si ocurre un error al leer el recurso.
     */
    private ResponseEntity<Resource> handleRangeRequest(
            Resource resource, String rangeHeader, long fileSize,
            String contentType, String fileName) throws IOException {

        try {
            // Parsear el header Range (ej: "bytes=0-1023")
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);

            if (ranges.isEmpty()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .contentLength(fileSize)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .body(resource);
            }

            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(fileSize);
            long end = range.getRangeEnd(fileSize);
            long rangeLength = end - start + 1;

            // Crear respuesta parcial (206 Partial Content)
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(rangeLength)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (Exception e) {
            // Si hay error parseando el range, devolver el archivo completo
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        }
    }
}
