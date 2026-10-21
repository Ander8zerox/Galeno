package com.galeno.partners;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PartnerRepository extends JpaRepository<Partner, UUID> {

    Optional<Partner> findByUsuarioId(UUID usuarioId);

    Optional<Partner> findByCodigo(String codigo);

    boolean existsByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);

    boolean existsByCodigo(String codigo);

    List<Partner> findAllByOrderByCreadoEnDesc();
}
