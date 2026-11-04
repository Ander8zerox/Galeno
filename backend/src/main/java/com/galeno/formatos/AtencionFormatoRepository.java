package com.galeno.formatos;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AtencionFormatoRepository extends JpaRepository<AtencionFormato, UUID> {

    List<AtencionFormato> findByAtencionIdOrderByOrden(UUID atencionId);

    List<AtencionFormato> findByAtencionIdInOrderByOrden(Collection<UUID> atencionIds);
}
