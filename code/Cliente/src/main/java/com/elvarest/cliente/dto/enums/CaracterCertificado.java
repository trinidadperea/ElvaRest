package com.elvarest.cliente.dto.enums;

/** Carácter de un certificado (mismo enum que el Servidor). */
public enum CaracterCertificado {
    DEFINITIVO("Definitivo"),
    PROVISORIO("Provisorio");

    private final String etiqueta;

    CaracterCertificado(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
