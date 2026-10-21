package com.galeno.plataforma;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OperadorPlataformaRepository extends JpaRepository<OperadorPlataforma, UUID> {
}
