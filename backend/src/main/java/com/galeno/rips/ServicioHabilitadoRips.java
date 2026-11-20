package com.galeno.rips;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.galeno.historia.ServicioHabilitado;
import com.galeno.modulos.Modulo;
import com.galeno.modulos.ModulosService;

/** El servicio que el consultorio fijó en los datos del prestador del RIPS, y sus datos del encuentro por defecto. */
@Component
class ServicioHabilitadoRips implements ServicioHabilitado {

    private final RipsPrestadorRepository prestadores;
    private final ReferenciasService referencias;
    private final ModulosService modulos;
    /** Perezoso: los cobros dependen de la historia, y la historia de este componente. */
    private final org.springframework.beans.factory.ObjectProvider<CobrosService> cobros;

    ServicioHabilitadoRips(RipsPrestadorRepository prestadores, ReferenciasService referencias, ModulosService modulos,
                           org.springframework.beans.factory.ObjectProvider<CobrosService> cobros) {
        this.cobros = cobros;
        this.prestadores = prestadores;
        this.referencias = referencias;
        this.modulos = modulos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> codigo() {
        return prestadores.findFirstBy().map(RipsPrestador::getServicio).filter(s -> !s.isBlank());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Encuentro> encuentroPorDefecto() {
        if (!modulos.activo(Modulo.RIPS)) {
            return Optional.empty();
        }
        return prestadores.findFirstBy().map(p -> new Encuentro(p.getModalidadAtencion(), p.getGrupoServicios(),
                p.getCausaExterna()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> entidadResponsable(java.util.UUID atencionId) {
        if (!modulos.activo(Modulo.RIPS)) {
            return Optional.empty();
        }
        var c = cobros.getObject().atencion(atencionId);
        return Optional.ofNullable(c.pagadorNombre())
                .map(n -> c.numeroAutorizacion() == null ? n : n + " · autorización " + c.numeroAutorizacion());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> nombreReferencia(String tabla, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return referencias.porCodigo(tabla, codigo).map(ReferenciasService.ReferenciaVista::nombre);
    }
}
