package com.galeno.rips;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PagadorRepository extends JpaRepository<Pagador, UUID> {

    List<Pagador> findAllByOrderByActivoDescNombre();

    Optional<Pagador> findFirstByTipo(Pagador.Tipo tipo);
}
