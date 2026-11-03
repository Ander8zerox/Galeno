package com.galeno.identidad;

import java.util.UUID;

/**
 * Si una cuenta es operadora de la consola de Galenum (para ella la verificación en dos pasos es obligatoria). Lo
 * implementa el módulo de plataforma (identidad no depende de él).
 */
public interface OperadoresPlataforma {

    boolean esOperador(UUID usuarioId);
}
