package com.galeno.historia;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CupsRepository extends JpaRepository<Cups, String> {

    /** Por prefijo de código (8902) o por texto en la descripción (resina); la colación ignora tildes. */
    @Query("""
            select c from Cups c
            where c.codigo like concat(:q, '%') or c.descripcion like concat('%', :q, '%')
            order by case when c.codigo like concat(:q, '%') then 0 else 1 end, c.codigo
            """)
    List<Cups> buscar(String q, Pageable pageable);
}
