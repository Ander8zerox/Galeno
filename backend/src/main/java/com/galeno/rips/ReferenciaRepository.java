package com.galeno.rips;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ReferenciaRepository extends JpaRepository<Referencia, Referencia.Clave> {

    List<Referencia> findByTablaAndHabilitadoTrueOrderByCodigo(String tabla);

    /** Por código o por nombre (la colación ignora tildes): para las tablas largas, como los municipios. */
    @Query("""
            select r from Referencia r
            where r.tabla = :tabla and r.habilitado = true
              and (r.codigo like concat(:q, '%') or r.nombre like concat('%', :q, '%'))
            order by case when r.codigo like concat(:q, '%') then 0 else 1 end, r.nombre
            """)
    List<Referencia> buscar(String tabla, String q, Pageable pageable);
}
