# Diseño: motor de formatos clínicos (primera especialidad: odontología)

Estado: **borrador para revisión** (3 de noviembre de 2026). Nada de esto está construido todavía.

## Problema

Hoy toda atención tiene los mismos campos de medicina general (motivo, enfermedad actual, revisión por sistemas, signos
vitales, examen físico, análisis, plan y diagnósticos CIE-10). Un odontólogo necesita otras cosas: odontograma,
periodontograma, examen estomatológico, plan de tratamiento con presupuesto. La competencia ofrece decenas de formatos
por especialidad.

Programar a mano cada especialidad no escala. La idea es un **motor**: un formato es un **dato** (una definición en
JSON), y un solo componente lo dibuja, lo valida, lo guarda, lo imprime en el PDF y se lo pasa a la IA.

## Principio: los formatos son opcionales

Odontología es solo la **primera** especialidad. Muchos consultorios (medicina general, psicología, nutrición…) no
van a usar nunca el formato odontológico, y para ellos nada debe cambiar:

- **Sin formatos activos, la atención es exactamente la de hoy.** Mismos campos, mismo PDF, mismo flujo de firma.
  Ningún consultorio nuevo ni existente tiene formatos activos por defecto.
- **Nada odontológico se ve si no se activa:** ni pestañas vacías, ni el odontograma, ni opciones en los menús.
- **El motor no sabe de odontología.** Lo odontológico vive en las definiciones de formato y en dos componentes
  especiales (`odontograma`, `periodontograma`); el resto del código es igual para cualquier especialidad.
- **Consultorios mixtos.** Un mismo consultorio puede tener un médico general y un odontólogo (el demo es así). Por
  eso activar un formato en el consultorio solo lo deja **disponible**: cada profesional elige cuáles usa por defecto
  y puede agregar o quitar formatos en cada atención. Al médico general no le aparece la historia odontológica.
- **El PDF y la IA** solo incluyen los formatos diligenciados en esa atención.
- **RIPS, facturación y fórmula** no dependen de los formatos, así que siguen igual para todos.

## Conceptos

| Concepto | Qué es |
|---|---|
| **Formato** | Una plantilla con nombre y especialidad ("Historia odontológica", "Escala de dolor EVA"). |
| **Versión de formato** | La definición concreta (secciones y campos). Una vez publicada **no cambia nunca**; para corregirla se publica la versión siguiente. |
| **Activación** | El consultorio elige qué formatos tiene disponibles. Desactivar no borra nada de lo ya diligenciado. |
| **Formatos por defecto del profesional** | Entre los disponibles, los que se agregan solos a las atenciones de ese profesional. |
| **Formato diligenciado** | Las respuestas de un formato dentro de una atención. Apunta a la versión exacta con la que se llenó. |

### Quién crea los formatos

- **Fase 1: solo Galenum** (catálogo global, mantenido por nosotros como migraciones o desde la consola de la plataforma).
- **Más adelante**: formatos propios del consultorio, con un editor visual. El editor es un proyecto en sí mismo; no
  vale la pena hasta saber qué piden los clientes.

## La definición de un formato (JSON)

```json
{
  "secciones": [
    {
      "clave": "habitos",
      "titulo": "Hábitos e higiene oral",
      "campos": [
        { "clave": "cepillado", "tipo": "seleccion", "etiqueta": "Cepillado al día",
          "opciones": ["1 vez", "2 veces", "3 o más"], "obligatorio": true },
        { "clave": "seda", "tipo": "si_no", "etiqueta": "Usa seda dental" },
        { "clave": "bruxismo", "tipo": "si_no", "etiqueta": "Bruxismo" },
        { "clave": "obs", "tipo": "texto_largo", "etiqueta": "Observaciones",
          "visibleSi": { "campo": "bruxismo", "igual": true } }
      ]
    }
  ]
}
```

**Tipos de campo:**
- **Básicos:** `texto`, `texto_largo`, `numero` (con unidad, mínimo y máximo), `fecha`, `seleccion`, `multiple`
  y `si_no`.
- **Tabla:** `tabla`, con columnas tipadas y filas fijas o libres.
- **Escala:** `escala`, con ítems con puntaje, total automático e interpretación por rangos, como Glasgow, EVA o
  el índice de placa.
- **Componentes especiales:** `odontograma` y `periodontograma`, con su propio formato de respuesta y su propio
  componente gráfico.

**Reglas:**
- `obligatorio` (se exige al **firmar**, no al guardar el borrador).
- `visibleSi` sencillo: depende del valor de otro campo.

**Claves estables.** Cada campo tiene una `clave` que no cambia entre versiones. Las respuestas se guardan por clave:
`{"habitos": {"cepillado": "2 veces", "seda": true}}`. Así una versión nueva puede añadir campos sin romper lo
anterior, y la IA y los reportes encuentran el mismo dato en todas las versiones.

## Almacenamiento (MySQL 8.4, columnas JSON)

```
formatos              id, codigo (único, p. ej. "odonto.historia"), nombre, especialidad, activo
formato_versiones     id, formato_id, version, definicion JSON, publicada_en      (inmutable)
formatos_consultorio  tenant_id, formato_id, activo, orden                        (activación)
formatos_profesional  tenant_id, usuario_id, formato_id                           (por defecto)
atencion_formatos     id, tenant_id, atencion_id, formato_version_id, respuestas JSON, orden
```

- **Catálogo global:** `formatos` y `formato_versiones` no tienen `tenant_id`. Los demás sí, con el mismo `@TenantId`
  que el resto de Galeno, así que un consultorio nunca ve las respuestas de otro.
- **Validación en el servidor:** al guardar, las respuestas se validan contra la definición (tipos, opciones válidas,
  rangos). Al firmar se validan además los campos obligatorios. El frontend valida lo mismo, pero solo para mostrar
  los errores; el servidor es el que decide.
- **Sin una tabla por campo:** columnas JSON y no una fila por respuesta (EAV). Se lee y escribe la atención entera de
  una vez, que es lo que hace la aplicación. Si más adelante se necesitan reportes por campo, MySQL puede indexar
  rutas JSON con columnas generadas.

## Inmutabilidad y firma

- `atencion_formatos` sigue a su atención: **mientras la atención es borrador se edita, y al firmarla queda de solo
  lectura**, con la misma regla que hoy protege a `Atencion`.
- Como cada respuesta apunta a una **versión exacta**, cambiar el formato después no altera las historias firmadas. El
  PDF de una atención de 2026 se ve igual en 2030.
- Las correcciones después de firmar siguen siendo **notas aclaratorias**, como hoy.
- Borrar un borrador borra también sus formatos diligenciados.

## Relación con los campos actuales de la atención

**No se reemplazan.**
- Motivo de consulta, diagnósticos CIE-10 y plan siguen en todas las atenciones: los RIPS y la fórmula los necesitan.
- Los formatos se **suman** a la atención.
- Lo que sí puede cambiar es **qué campos generales se muestran**. Por ejemplo, el consultorio odontológico puede
  ocultar la revisión por sistemas o los signos vitales. Esto sería un ajuste del consultorio, separado del motor;
  puede quedar para después.

## Odontograma

- **Numeración FDI:** 32 dientes permanentes (11–48) y 20 temporales (51–85). Cada diente tiene 5 superficies:
  vestibular, lingual o palatina, mesial, distal y oclusal o incisal.
- **Respuesta:**
  `{"denticion": "permanente", "dientes": {"36": {"estado": "presente", "superficies": {"O": "caries", "M": "obturado"}}, "18": {"estado": "ausente"}}}`
- **Hallazgos (catálogo fijo en la fase 2):**
  - Por superficie: caries, obturado (resina o amalgama) y sellante.
  - Por diente: ausente, extracción indicada, corona, endodoncia, implante, prótesis y fractura.
- **Inicial y evolución:** el odontograma se guarda en cada atención. Una atención nueva **parte del último
  odontograma firmado** del paciente, como hoy se traen los antecedentes. El primero es el inicial, y la historia
  muestra cómo cambió.
- **Componente gráfico:** un SVG propio en React, sin librería externa: clic en una superficie y se elige el hallazgo.
- **PDF:** el generador actual (openhtmltopdf) no dibuja SVG sin agregar Batik, que pesa varios MB. Se propone
  dibujarlo en el PDF como **cuadrícula HTML/CSS**, una celda por diente con sus superficies coloreadas. Es más
  liviano y se puede imprimir; se revisa en la fase 2 si alcanza.

## Plan de tratamiento y presupuesto (fase 3)

No es un campo del formato. Es un objeto **del paciente** que dura varias sesiones:
- Fases con procedimientos (CUPS de odontología), diente o superficie y valor.
- El paciente lo acepta (firma o constancia).
- Cada evolución por sesión marca los procedimientos realizados.

Tiene tablas propias y se diseña en detalle al llegar a esa fase.

## PDF e IA

- **PDF:** un renderizador genérico por tipo de campo. Las secciones van después de los campos generales de la
  atención, solo las que tienen datos.
- **IA:** las respuestas se pasan como texto ordenado ("Hábitos e higiene oral — Cepillado al día: 2 veces…") en el
  contexto del diagnóstico. Solo se envía lo diligenciado, nunca la definición completa.

## Permisos

| Acción | Quién |
|---|---|
| Activar o desactivar formatos en el consultorio | ADMIN |
| Elegir sus formatos por defecto | Cada MÉDICO, entre los activos del consultorio |
| Diligenciar formatos en una atención | MÉDICO (el dueño de la atención, como hoy) |
| Ver los formatos diligenciados | Quien hoy puede ver la historia clínica (la secretaria no) |
| Crear o publicar formatos del catálogo | Operador de la plataforma (o migración) |

## Fases

1. **Motor y historia odontológica:**
   - Tablas, validación y activación por consultorio.
   - Componente genérico para los tipos básicos, `tabla` y `escala`.
   - Formato "Historia odontológica" (antecedentes odontológicos, examen estomatológico, hábitos e índice de placa).
   - PDF e IA.
2. **Odontograma:** componente SVG, inicial y evolución, y la cuadrícula en el PDF.
3. **Periodontograma, plan de tratamiento con presupuesto, consentimientos odontológicos y CUPS de odontología.**
4. **Formatos transversales:** incapacidad, remisión y contrarreferencia, y consentimiento firmado por el paciente.

Cada fase se puede desplegar sola y ya le sirve a un consultorio. Las fases 1 a 3 solo le sirven a quien activa
odontología; la fase 4 le sirve a todos (ver la decisión 5).

## Decisiones abiertas

1. **¿Formatos propios del consultorio en la fase 1?** Propuesta: no. Solo el catálogo de Galenum hasta ver qué piden.
2. **¿Varios formatos en una misma atención?** Propuesta: sí, por ejemplo historia odontológica más una escala de dolor.
3. **¿Ocultar campos generales por consultorio** (revisión por sistemas, signos vitales) desde la fase 1, o después?
4. **¿Quién arma el contenido clínico** de la historia odontológica? Hace falta un odontólogo que revise los campos;
   el motor no sirve si el formato no es el que usa un consultorio real.
5. **¿Adelantar formatos transversales?** Como no todos los consultorios son odontológicos, una opción es construir
   el motor (fase 1) estrenándolo con algo que sirva a todos, como incapacidad y remisión, y dejar odontología para
   después. Gana: todos los clientes ven valor desde la primera entrega. Pierde: se demora la especialidad que
   motivó el motor y que más nos diferencia frente a la competencia.
