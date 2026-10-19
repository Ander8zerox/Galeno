package com.galeno.pacientes;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);

    @Query("""
            select p from Paciente p
            where :q is null
               or p.numeroDocumento like concat(:q, '%')
               or lower(concat(p.nombres, ' ', p.apellidos)) like lower(concat('%', :q, '%'))
            """)
    Page<Paciente> search(String q, Pageable pageable);
}
