package com.galeno.rips;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.historia.ServicioHabilitado;

/** El servicio que el consultorio fijó en los datos del prestador del RIPS. */
@Component
class ServicioHabilitadoRips implements ServicioHabilitado {

    private final RipsPrestadorRepository prestadores;

    ServicioHabilitadoRips(RipsPrestadorRepository prestadores) {
        this.prestadores = prestadores;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> codigo() {
        return prestadores.findFirstBy().map(RipsPrestador::getServicio).filter(s -> !s.isBlank());
    }
}
