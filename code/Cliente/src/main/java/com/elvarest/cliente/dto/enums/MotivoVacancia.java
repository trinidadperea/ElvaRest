package com.elvarest.cliente.dto.enums;

/**
 * Motivo por el que un cargo queda vacante. Se elige de una lista en los formularios de
 * concurso y de designación; el texto libre se usa en "Otro" o como detalle opcional de
 * los demás motivos.
 *
 * El texto que se persiste en {@code origenVacancia} se arma con {@link #texto(MotivoVacancia, String)}
 * y se vuelve a descomponer con {@link #desdeTexto(String)} / {@link #detalleDesdeTexto(String)},
 * porque ese campo es el que heredan las designaciones creadas desde el concurso.
 */
public enum MotivoVacancia {

    RENUNCIA("Renuncia"),
    CREACION_CARGO("Creación de cargo"),
    LICENCIA("Licencia"),
    JUBILACION("Jubilación"),
    OTRO("Otro");

    /** Separador entre el motivo y su detalle libre. */
    public static final String SEPARADOR = " – ";

    private final String etiqueta;

    MotivoVacancia(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Texto que se guarda en origenVacancia: "Otro" guarda únicamente lo escrito por el
     * usuario; los demás motivos combinan la etiqueta con el detalle cuando se cargó.
     * Es idempotente: si el detalle ya repite el motivo ("Renuncia" o "Renuncia del
     * titular") se recorta antes de componer, para no guardar "Renuncia – Renuncia".
     */
    public static String texto(MotivoVacancia motivo, String detalle) {
        if (motivo == null) {
            return null;
        }
        String limpio = detalle == null ? "" : detalle.trim();
        if (motivo == OTRO) {
            return limpio.isEmpty() ? null : limpio;
        }
        limpio = sinElMotivo(motivo, limpio);
        return limpio.isEmpty() ? motivo.etiqueta : motivo.etiqueta + SEPARADOR + limpio;
    }

    /**
     * Motivo deducido de un texto guardado: se usa al editar registros anteriores al
     * selector. Un texto libre que no coincide con ninguna etiqueta vuelve como "Otro"
     * para no perder lo que se había escrito.
     */
    public static MotivoVacancia desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim();
        for (MotivoVacancia motivo : values()) {
            if (motivo == OTRO) {
                continue;
            }
            // "Renuncia", "Renuncia – detalle" y también "Renuncia del titular" (textos
            // anteriores al selector) se reconocen como el mismo motivo.
            if (limpio.equalsIgnoreCase(motivo.etiqueta)
                    || empiezaCon(limpio, motivo.etiqueta + SEPARADOR)
                    || empiezaCon(limpio, motivo.etiqueta + " ")) {
                return motivo;
            }
        }
        return OTRO;
    }

    /**
     * Detalle libre contenido en un texto guardado, sin el motivo cuando ya lo incluye.
     * Con "Otro" devuelve el texto completo, porque ese texto no se descompone.
     */
    public static String detalleDesdeTexto(String texto) {
        return detalleDesdeTexto(desdeTexto(texto), texto);
    }

    /**
     * Detalle libre de un texto guardado según el motivo ya elegido (no el deducido), que es
     * el que se va a recomponer al guardar. Con un motivo de la lista recorta la etiqueta
     * inicial; con "Otro" o sin motivo devuelve el texto tal cual para no perder información.
     */
    public static String detalleDesdeTexto(MotivoVacancia motivo, String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        if (motivo == null || motivo == OTRO) {
            return texto.trim();
        }
        String detalle = sinElMotivo(motivo, texto.trim());
        return detalle.isEmpty() ? null : detalle;
    }

    /** Quita la etiqueta del motivo (con separador o con espacio) cuando el texto ya la repite. */
    private static String sinElMotivo(MotivoVacancia motivo, String texto) {
        String limpio = texto == null ? "" : texto.trim();
        boolean recortado = true;
        while (recortado && !limpio.isEmpty()) {
            recortado = false;
            if (empiezaCon(limpio, motivo.etiqueta + SEPARADOR)) {
                limpio = limpio.substring(motivo.etiqueta.length() + SEPARADOR.length()).trim();
                recortado = true;
            } else if (empiezaCon(limpio, motivo.etiqueta + " ")) {
                limpio = limpio.substring(motivo.etiqueta.length() + 1).trim();
                recortado = true;
            } else if (limpio.equalsIgnoreCase(motivo.etiqueta)) {
                limpio = "";
                recortado = true;
            }
        }
        return limpio;
    }

    private static boolean empiezaCon(String texto, String prefijo) {
        return texto.length() >= prefijo.length() && texto.regionMatches(true, 0, prefijo, 0, prefijo.length());
    }
}
