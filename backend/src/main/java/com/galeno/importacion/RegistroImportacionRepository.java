package com.galeno.importacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RegistroImportacionRepository extends JpaRepository<RegistroImportacion, UUID> {

    Page<RegistroImportacion> findByLoteIdOrderByOrden(UUID loteId, Pageable pageable);

    Page<RegistroImportacion> findByLoteIdAndEstadoOrderByOrden(UUID loteId, RegistroImportacion.Estado estado,
                                                                Pageable pageable);

    List<RegistroImportacion> findByLoteIdOrderByOrden(UUID loteId);

    Optional<RegistroImportacion> findByIdAndLoteId(UUID id, UUID loteId);

    long countByLoteIdAndIncluidoTrue(UUID loteId);

    @Modifying
    @Query("update RegistroImportacion r set r.incluido = :incluido where r.loteId = :loteId and r.estado <> com.galeno.importacion.RegistroImportacion.Estado.ERROR")
    int incluirTodos(UUID loteId, boolean incluido);

    @Modifying
    @Query("delete from RegistroImportacion r where r.loteId = :loteId")
    int borrarDelLote(UUID loteId);
}
