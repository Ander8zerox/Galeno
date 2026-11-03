# Consultorio demo

Archivos para llenar un consultorio de demostración en producción. **Todos los datos son inventados.** Los documentos
empiezan por 99 para no coincidir con cédulas expedidas, y los teléfonos son 300 000 0XXX.

| Archivo | Qué trae | Dónde se sube |
|---|---|---|
| `pacientes-demo.csv` | 12 pacientes (10 adultos, una menor con TI y un niño con RC) | Importar → Listado de pacientes |
| `rips-demo.json` | 24 atenciones pasadas (mayo a octubre de 2026) de esos mismos pacientes: medicina general y odontología, con CUPS y CIE-10 | Importar → RIPS |

## Pasos

1. Registrar el consultorio con `demo@galenum.com.co` (el alias `*` de ImprovMX reenvía la activación) y activar la MFA
   del administrador.
2. En la consola de la plataforma, regalarle días a la suscripción para que la prueba no venza en medio de una
   demostración.
3. Subir **primero** `pacientes-demo.csv`: Galenum reconoce las 7 columnas solo. Revisar y confirmar.
4. Subir después `rips-demo.json`, revisar y confirmar. Las atenciones quedan como "importadas de RIPS", y si hace
   falta se pueden deshacer desde el mismo lote.
5. Para que se vea el flujo completo, crear a mano 2 o 3 citas para esta semana y una atención firmada con fórmula
   (por ejemplo, con María Fernanda Rojas o Carlos Andrés Gómez).

Probado el 3 de noviembre de 2026 en el esquema de pruebas: 12 pacientes creados y 24 atenciones importadas, sin errores.

Para regenerarlos: `node tools/demo/gen-demo.mjs docs/demo`.
