package com.elvarest.cliente.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Interpreta, solo para mostrarlo, el detalle de cada evento. Los eventos nuevos traen una frase
 * en lenguaje común ("Se eliminó el concurso «X»"), que se muestra tal cual como descripción. Los
 * eventos anteriores guardaron un JSON breve: un mapa plano {campo: valor} en las altas y un diff
 * {campo: {antes, despues}} en las modificaciones, que se sigue mostrando como tabla. Si el texto
 * parece JSON pero no es válido (o no tiene esa forma) devuelve el texto crudo para mostrarlo tal
 * cual: el detalle nunca debe romper la pantalla.
 */
public final class AuditoriaDetalleParser {

    private static final String VACIO = "(vacío)";

    private AuditoriaDetalleParser() {
    }

    public static Resultado interpretar(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return new Resultado(Collections.emptyList(), false, null, null);
        }
        if (!detalle.trim().startsWith("{")) {
            return new Resultado(Collections.emptyList(), false, null, detalle.trim());
        }
        try {
            Object raiz = new Lector(detalle).leerDocumento();
            if (!(raiz instanceof Map<?, ?> mapa)) {
                return new Resultado(Collections.emptyList(), false, detalle, null);
            }
            List<Fila> filas = new ArrayList<>();
            boolean hayDiff = false;
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                String campo = String.valueOf(entrada.getKey());
                Object valor = entrada.getValue();
                if (valor instanceof Map<?, ?> cambio && (cambio.containsKey("antes") || cambio.containsKey("despues"))) {
                    filas.add(new Fila(campo, texto(cambio.get("antes")), texto(cambio.get("despues")), true));
                    hayDiff = true;
                } else {
                    filas.add(new Fila(campo, null, texto(valor), false));
                }
            }
            return new Resultado(filas, hayDiff, null, null);
        } catch (RuntimeException e) {
            return new Resultado(Collections.emptyList(), false, detalle, null);
        }
    }

    private static String texto(Object valor) {
        return valor == null ? VACIO : valor.toString();
    }

    /** Fila de la tabla de cambios: en un alta solo "valor"; en una modificación "antes" y "valor" (después). */
    public static final class Fila {
        private final String campo;
        private final String antes;
        private final String valor;
        private final boolean diff;

        Fila(String campo, String antes, String valor, boolean diff) {
            this.campo = campo;
            this.antes = antes;
            this.valor = valor;
            this.diff = diff;
        }

        public String getCampo() {
            return campo;
        }

        public String getAntes() {
            return antes;
        }

        public String getValor() {
            return valor;
        }

        public boolean isDiff() {
            return diff;
        }
    }

    /** Resultado inmutable listo para la vista. */
    public static final class Resultado {
        private final List<Fila> filas;
        private final boolean modoDiff;
        private final String crudo;
        private final String descripcion;

        Resultado(List<Fila> filas, boolean modoDiff, String crudo, String descripcion) {
            this.filas = Collections.unmodifiableList(filas);
            this.modoDiff = modoDiff;
            this.crudo = crudo;
            this.descripcion = descripcion;
        }

        public List<Fila> getFilas() {
            return filas;
        }

        /** true si hay columnas antes/después (modificación); false si es una lista simple de valores. */
        public boolean isModoDiff() {
            return modoDiff;
        }

        /** Texto original, solo cuando no se pudo interpretar como JSON; si no, null. */
        public String getCrudo() {
            return crudo;
        }

        /** Frase en lenguaje común de los eventos nuevos; null en los que guardaron JSON. */
        public String getDescripcion() {
            return descripcion;
        }

        public boolean isSinDetalle() {
            return filas.isEmpty() && crudo == null && descripcion == null;
        }
    }

    /** Lector mínimo del subconjunto de JSON que genera el Servidor: objetos, cadenas y literales. */
    private static final class Lector {
        private final String s;
        private int i = 0;

        Lector(String s) {
            this.s = s;
        }

        Object leerDocumento() {
            Object valor = leerValor();
            saltar();
            if (i != s.length()) {
                throw error();
            }
            return valor;
        }

        private Object leerValor() {
            saltar();
            if (i >= s.length()) {
                throw error();
            }
            char c = s.charAt(i);
            if (c == '{') {
                return leerObjeto();
            }
            if (c == '"') {
                return leerCadena();
            }
            return leerLiteral();
        }

        private Map<String, Object> leerObjeto() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            i++; // '{'
            saltar();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return mapa;
            }
            while (true) {
                saltar();
                String clave = leerCadena();
                saltar();
                esperar(':');
                mapa.put(clave, leerValor());
                saltar();
                if (i >= s.length()) {
                    throw error();
                }
                char c = s.charAt(i++);
                if (c == '}') {
                    return mapa;
                }
                if (c != ',') {
                    throw error();
                }
            }
        }

        private String leerCadena() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (i >= s.length()) {
                    throw error();
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) {
                    throw error();
                }
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 > s.length()) {
                            throw error();
                        }
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> throw error();
                }
            }
        }

        /** Números, booleanos y null: se leen como texto hasta el próximo separador. */
        private Object leerLiteral() {
            int inicio = i;
            while (i < s.length() && s.charAt(i) != ',' && s.charAt(i) != '}'
                    && !Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            String token = s.substring(inicio, i);
            if (token.isEmpty()) {
                throw error();
            }
            return "null".equals(token) ? null : token;
        }

        private void esperar(char esperado) {
            if (i >= s.length() || s.charAt(i) != esperado) {
                throw error();
            }
            i++;
        }

        private void saltar() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        private IllegalArgumentException error() {
            return new IllegalArgumentException("Detalle de auditoría no interpretable");
        }
    }
}