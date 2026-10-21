package com.galeno.partners;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReferidoRepository extends JpaRepository<Referido, UUID> {

    List<Referido> findByPartnerIdOrderByAtribuidoEnDesc(UUID partnerId);

    long countByPartnerId(UUID partnerId);
}
