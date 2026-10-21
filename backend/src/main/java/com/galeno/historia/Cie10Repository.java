package com.galeno.historia;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface Cie10Repository extends JpaRepository<Cie10, String> {

    /** Por prefijo de código (J02) o por texto en la descripción (faringitis); la colación ignora tildes. */
    @Query("""
            select c from Cie10 c
            where c.codigo like concat(:q, '%') or c.descripcion like concat('%', :q, '%')
            order by case when c.codigo like concat(:q, '%') then 0 else 1 end, c.codigo
            """)
    List<Cie10> buscar(String q, Pageable pageable);
}
