package com.galeno.shared.correo;

/** Un correo saliente en texto plano (los clientes de correo lo muestran bien y no hay HTML que escapar). */
public record Correo(String para, String asunto, String texto) {
}
