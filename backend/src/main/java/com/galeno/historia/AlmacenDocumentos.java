package com.galeno.historia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Dónde se guardan los archivos de la historia. {@code app.documentos.almacen}: {@code disco} en desarrollo
 * (carpeta {@code app.documentos.dir}) o {@code s3} en AWS (bucket privado y cifrado). La clave es
 * {@code consultorio/paciente/uuid} y un documento nunca se sobrescribe.
 */
interface AlmacenDocumentos {

    void guardar(String clave, byte[] contenido);

    byte[] leer(String clave);

    @Component
    @ConditionalOnProperty(name = "app.documentos.almacen", havingValue = "disco", matchIfMissing = true)
    class EnDisco implements AlmacenDocumentos {

        private final Path raiz;

        EnDisco(@Value("${app.documentos.dir}") String dir) {
            this.raiz = Path.of(dir).toAbsolutePath().normalize();
        }

        @Override
        public void guardar(String clave, byte[] contenido) {
            Path destino = resolver(clave);
            try {
                Files.createDirectories(destino.getParent());
                // CREATE_NEW: un documento nunca se sobrescribe
                Files.write(destino, contenido, StandardOpenOption.CREATE_NEW);
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo guardar el documento", e);
            }
        }

        @Override
        public byte[] leer(String clave) {
            try {
                return Files.readAllBytes(resolver(clave));
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo leer el documento", e);
            }
        }

        private Path resolver(String clave) {
            Path ruta = raiz.resolve(clave).normalize();
            if (!ruta.startsWith(raiz)) {
                throw new IllegalArgumentException("Clave de documento no válida");
            }
            return ruta;
        }
    }

    /**
     * Amazon S3. El cifrado, el bloqueo de acceso público y el versionado son del bucket (los crea Terraform: SSE-KMS
     * por defecto y una política que rechaza conexiones sin TLS), no de cada petición. {@code If-None-Match: *} hace
     * que S3 rechace escribir sobre una clave que ya existe, igual que {@code CREATE_NEW} en disco. Credenciales: rol
     * de la instancia.
     */
    @Component
    @ConditionalOnProperty(name = "app.documentos.almacen", havingValue = "s3")
    class EnS3 implements AlmacenDocumentos, AutoCloseable {

        private final S3Client s3;
        private final String bucket;

        EnS3(@Value("${app.documentos.bucket:}") String bucket,
             @Value("${app.documentos.region:us-east-1}") String region,
             @Value("${app.documentos.endpoint:}") String endpoint) {
            if (bucket.isBlank()) {
                throw new IllegalStateException("Falta app.documentos.bucket (APP_DOCUMENTOS_BUCKET) para guardar en S3");
            }
            this.bucket = bucket;
            var builder = S3Client.builder().region(Region.of(region));
            if (!endpoint.isBlank()) {
                // Solo para probar en local con un S3 compatible (MinIO); en AWS queda vacío
                builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
            }
            this.s3 = builder.build();
        }

        @Override
        public void guardar(String clave, byte[] contenido) {
            var req = PutObjectRequest.builder().bucket(bucket).key(clave).ifNoneMatch("*").build();
            try {
                s3.putObject(req, RequestBody.fromBytes(contenido));
            } catch (S3Exception e) {
                throw new IllegalStateException("No se pudo guardar el documento en S3 (" + e.statusCode() + ")", e);
            }
        }

        @Override
        public byte[] leer(String clave) {
            try {
                return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(clave).build()).asByteArray();
            } catch (S3Exception e) {
                throw new IllegalStateException("No se pudo leer el documento de S3 (" + e.statusCode() + ")", e);
            }
        }

        @Override
        public void close() {
            s3.close();
        }
    }
}
