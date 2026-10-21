package com.galeno.partners;

/** Lo que el partner diligencia al inscribirse (también se guarda como JSON mientras verifica el correo). */
record DatosPartner(String nombreCuenta, Partner.TipoPersona tipoPersona, String nombre, String tipoDocumento,
                    String numeroDocumento, String telefono, String banco, String tipoCuenta, String numeroCuenta) {
}
