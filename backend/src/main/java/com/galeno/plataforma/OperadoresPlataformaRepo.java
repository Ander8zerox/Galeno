package com.galeno.plataforma;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.galeno.identidad.OperadoresPlataforma;

/** Aparte de {@link PlataformaService}, que depende de identidad: así identidad puede preguntar sin ciclo. */
@Component
class OperadoresPlataformaRepo implements OperadoresPlataforma {

    private final OperadorPlataformaRepository operadores;

    OperadoresPlataformaRepo(OperadorPlataformaRepository operadores) {
        this.operadores = operadores;
    }

    /** Se llama dentro de una transacción con el tenant del sistema (ver identidad.MfaService). */
    @Override
    public boolean esOperador(UUID usuarioId) {
        return operadores.existsById(usuarioId);
    }
}
