package com.galeno.importacion;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PlantillaImportacionRepository extends JpaRepository<PlantillaImportacion, UUID> {

    Optional<PlantillaImportacion> findByFirmaHash(String firmaHash);
}
