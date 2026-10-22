package com.galeno.shared.correo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CorreoSuprimidoRepository extends JpaRepository<CorreoSuprimido, String> {

    List<CorreoSuprimido> findAllByOrderByUltimoEnDesc();
}
