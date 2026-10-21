package com.galeno.historia;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dónde se guardan los archivos de la historia. Hoy en disco local (desarrollo y Capa 1);
 * en AWS se reemplaza por una implementación sobre S3 cifrado con la misma interfaz.
 */
interface AlmacenDocumentos {

    void guardar(String clave, byte[] contenido);

    byte[] leer(String clave);

    @Component
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
}
