package com.galeno.agenda;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Qué citas ya tienen preconsulta. Lo implementa el módulo de historia (agenda no depende de él). */
public interface PreconsultasDeCitas {

    Set<UUID> conPreconsulta(Collection<UUID> citaIds);
}
