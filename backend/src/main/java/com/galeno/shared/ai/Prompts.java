package com.galeno.shared.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Prompts versionados en {@code resources/prompts/<nombre>.md}. El nombre (p. ej. {@code formulacion-v1})
 * se guarda en cada trabajo de IA para poder auditar qué instrucciones produjo cada sugerencia.
 */
@Component
public class Prompts {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String version) {
        return cache.computeIfAbsent(version, v -> {
            try {
                return new ClassPathResource("prompts/" + v + ".md").getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("No existe el prompt " + v, e);
            }
        });
    }
}
