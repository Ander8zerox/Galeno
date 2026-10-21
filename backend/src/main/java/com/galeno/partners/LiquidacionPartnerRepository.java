package com.galeno.partners;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LiquidacionPartnerRepository extends JpaRepository<LiquidacionPartner, UUID> {

    List<LiquidacionPartner> findByPartnerIdOrderByCreadaEnDesc(UUID partnerId);
}
