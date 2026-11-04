package com.galeno.formatos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;

/**
 * Publica el catálogo de formatos de {@code resources/formatos/*.json} al arrancar: un archivo por versión
 * ({@code odonto.historia.v1.json}). Una versión nueva se publica sola; si un archivo de una versión ya publicada
 * cambió, el arranque falla: las versiones publicadas son inmutables (para corregir, se agrega la versión siguiente).
 */
@Component
class CatalogoFormatos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogoFormatos.class);

    /** Contenido de un archivo del catálogo. */
    record ArchivoFormato(String codigo, String nombre, String especialidad, String descripcion, int version,
                          JsonNode definicion) {
    }

    private final FormatoRepository formatos;
    private final FormatoVersionRepository versiones;
    private final TransactionTemplate tx;

    CatalogoFormatos(FormatoRepository formatos, FormatoVersionRepository versiones, TransactionTemplate tx) {
        this.formatos = formatos;
        this.versiones = versiones;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] archivos = new PathMatchingResourcePatternResolver().getResources("classpath*:formatos/*.json");
        for (Resource r : archivos) {
            try (InputStream in = r.getInputStream()) {
                ArchivoFormato a = leer(in, r.getFilename());
                tx.executeWithoutResult(s -> publicar(a));
            }
        }
    }

    /** Lee y verifica un archivo del catálogo (también lo usan las pruebas). */
    static ArchivoFormato leer(InputStream in, String nombreArchivo) {
        ArchivoFormato a = DefinicionFormato.JSON.readValue(in, ArchivoFormato.class);
        String donde = a.codigo() + " v" + a.version();
        if (a.codigo() == null || !a.codigo().matches("[a-z][a-z0-9_.]{1,59}") || a.version() < 1 || a.nombre() == null
                || a.especialidad() == null || a.definicion() == null) {
            throw new IllegalStateException("Archivo de formato incompleto: " + nombreArchivo);
        }
        if (!nombreArchivo.equals(a.codigo() + ".v" + a.version() + ".json")) {
            throw new IllegalStateException("El archivo " + nombreArchivo + " debe llamarse " + a.codigo() + ".v" + a.version() + ".json");
        }
        DefinicionFormato.leer(a.definicion()).verificar(donde);
        return a;
    }

    private void publicar(ArchivoFormato a) {
        Formato f = formatos.findByCodigo(a.codigo()).orElseGet(() -> new Formato(a.codigo()));
        f.describir(a.nombre(), a.especialidad(), a.descripcion());
        f = formatos.saveAndFlush(f);
        String definicion = DefinicionFormato.JSON.writeValueAsString(a.definicion());
        String hash = sha256(definicion);
        var publicada = versiones.findByFormatoIdAndVersion(f.getId(), a.version());
        if (publicada.isPresent()) {
            if (!publicada.get().getHash().equals(hash)) {
                throw new IllegalStateException("La versión " + a.version() + " del formato " + a.codigo()
                        + " ya está publicada y su archivo cambió. Las versiones publicadas no se modifican: publique la v"
                        + (a.version() + 1) + ".");
            }
            return;
        }
        versiones.save(new FormatoVersion(f.getId(), a.version(), definicion, hash));
        log.info("Formato publicado: {} v{}", a.codigo(), a.version());
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
