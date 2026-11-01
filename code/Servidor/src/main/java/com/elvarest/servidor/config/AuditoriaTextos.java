package com.elvarest.servidor.config;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.BaseEntity;
import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Curso;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.Licencia;
import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.entities.Persona;
import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.entities.enums.TipoCargo;
import com.elvarest.servidor.services.ReglasConcurso;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Redacta en lenguaje común el detalle de cada evento del historial (pensado para quien no conoce
 * el sistema por dentro): qué registro es, qué se hizo y, en las modificaciones, qué valor había
 * antes y cuál hay ahora. Solo arma texto: no lee ni escribe en la base.
 */
final class AuditoriaTextos {

    private static final int LARGO_MAXIMO_VALOR = 80;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Identificadores internos: no significan nada para quien lee el historial. */
    private static final Set<String> CAMPOS_OCULTOS = Set.of("concursoId", "concursoOrigenId");

    /** Nombre legible de cada campo; los que no figuran se muestran separando las palabras del nombre. */
    private static final Map<String, String> CAMPOS = Map.ofEntries(
            Map.entry("fechaApertura", "fecha de apertura"),
            Map.entry("fechaCierre", "fecha de cierre"),
            Map.entry("descripcion", "descripción"),
            Map.entry("estadoConcurso", "estado del concurso"),
            Map.entry("caracterDesignacion", "carácter de la designación"),
            Map.entry("origenVacancia", "origen de la vacancia"),
            Map.entry("motivoVacancia", "motivo de la vacancia"),
            Map.entry("profesorReemplazante", "profesor reemplazante"),
            Map.entry("esRellamado", "es rellamado"),
            Map.entry("horasTotales", "horas totales"),
            Map.entry("horasTransferidas", "horas transferidas"),
            Map.entry("cargaHoraria", "carga horaria"),
            Map.entry("tipoCargo", "tipo de cargo"),
            Map.entry("area", "área"),
            Map.entry("anio", "año"),
            Map.entry("fechaNacimiento", "fecha de nacimiento"),
            Map.entry("numeroDocumento", "número de documento"),
            Map.entry("telefono", "teléfono"),
            Map.entry("correoElectronico", "correo electrónico"),
            Map.entry("fechaInicioEfectiva", "fecha de inicio efectiva"),
            Map.entry("fechaFinDefinitiva", "fecha de fin definitiva"),
            Map.entry("estadoDesignacion", "estado de la designación"),
            Map.entry("fechaInicio", "fecha de inicio"),
            Map.entry("fechaFin", "fecha de fin"),
            Map.entry("caracter", "carácter"),
            Map.entry("fechaVencimiento", "fecha de vencimiento"),
            Map.entry("fechaCarga", "fecha de carga"),
            Map.entry("codigo", "código"),
            Map.entry("permiteProvisorio", "permite provisorio"),
            Map.entry("fechaEmision", "fecha de emisión"),
            Map.entry("horasElegidas", "horas elegidas"),
            Map.entry("estadoPostulacion", "estado de la postulación"),
            Map.entry("certificadosRecibidos", "certificados recibidos"),
            Map.entry("requiereVencimientoCertificados", "requiere vencimiento de certificados"),
            Map.entry("mesesVigencia", "meses de vigencia"),
            Map.entry("observacion", "observación"),
            Map.entry("leida", "leída"),
            Map.entry("fechaResolucion", "fecha de resolución"),
            Map.entry("nombreUsuario", "nombre de usuario"),
            Map.entry("debeCambiarPassword", "debe cambiar la contraseña"),
            Map.entry("concursoDocente", "postulación"),
            Map.entry("tipoCertificado", "tipo de certificado"));

    private AuditoriaTextos() {
    }

    /** Nombre corto con el que se identifica el registro: «Matemática», «Pérez, Ana», etc. */
    static String etiqueta(BaseEntity<?> entidad) {
        if (entidad instanceof Usuario usuario) {
            return textoONulo(usuario.getNombreUsuario(), entidad);
        }
        if (entidad instanceof Docente docente) {
            return nombreCompleto(docente);
        }
        if (entidad instanceof Concurso concurso) {
            return textoONulo(concurso.getDescripcion(), entidad);
        }
        if (entidad instanceof Cargo cargo) {
            if (esTexto(cargo.getDescripcion())) {
                return cargo.getDescripcion().trim();
            }
            if (cargo.getMateria() != null && esTexto(cargo.getMateria().getNombre())) {
                return cargo.getMateria().getNombre().trim();
            }
            return numero(entidad);
        }
        if (entidad instanceof Materia materia) {
            return textoONulo(materia.getNombre(), entidad);
        }
        if (entidad instanceof Curso curso) {
            return curso.getAnio() + "° año";
        }
        if (entidad instanceof Designacion designacion) {
            return designacion.getDocente() != null ? etiqueta(designacion.getDocente()) : numero(entidad);
        }
        if (entidad instanceof Licencia licencia) {
            return textoONulo(licencia.getTipo(), entidad);
        }
        if (entidad instanceof TipoCertificado tipo) {
            return textoONulo(tipo.getNombre(), entidad);
        }
        if (entidad instanceof Certificado certificado) {
            String tipo = certificado.getTipoCertificado() != null
                    ? etiqueta(certificado.getTipoCertificado()) : numero(entidad);
            ConcursoDocente postulacion = certificado.getConcursoDocente();
            return postulacion != null && postulacion.getDocente() != null
                    ? tipo + " de " + etiqueta(postulacion.getDocente()) : tipo;
        }
        if (entidad instanceof ConcursoDocente postulacion) {
            String docente = postulacion.getDocente() != null ? etiqueta(postulacion.getDocente()) : numero(entidad);
            return postulacion.getConcurso() != null
                    ? docente + " en " + etiqueta(postulacion.getConcurso()) : docente;
        }
        if (entidad instanceof Alerta alerta) {
            return abreviar(textoONulo(alerta.getMensaje(), entidad));
        }
        return numero(entidad);
    }

    /** Frase nominal con artículo: «el concurso «X»», «la designación de «Pérez, Ana»», etc. */
    static String frase(BaseEntity<?> entidad) {
        String nombre = etiqueta(entidad);
        if (entidad instanceof Usuario) {
            return "el usuario " + entre(nombre);
        }
        if (entidad instanceof Docente docente) {
            return (docente.getTipo() == TipoCargo.PRECEPTOR ? "el preceptor " : "el docente ") + entre(nombre);
        }
        if (entidad instanceof Concurso) {
            return "el concurso " + entre(nombre);
        }
        if (entidad instanceof Cargo) {
            return "el cargo " + entre(nombre);
        }
        if (entidad instanceof Materia) {
            return "la materia " + entre(nombre);
        }
        if (entidad instanceof Curso) {
            return "el curso " + entre(nombre);
        }
        if (entidad instanceof Designacion designacion) {
            return designacion.getDocente() != null
                    ? "la designación de " + entre(nombre) : "la designación " + entre(nombre);
        }
        if (entidad instanceof Licencia licencia) {
            String periodo = licencia.getFechaInicio() != null && licencia.getFechaFin() != null
                    ? " (del " + FECHA.format(licencia.getFechaInicio()) + " al " + FECHA.format(licencia.getFechaFin()) + ")"
                    : "";
            return "la licencia " + entre(nombre) + periodo;
        }
        if (entidad instanceof TipoCertificado) {
            return "el tipo de certificado " + entre(nombre);
        }
        if (entidad instanceof Certificado) {
            return "el certificado " + entre(nombre);
        }
        if (entidad instanceof ConcursoDocente postulacion) {
            if (postulacion.getDocente() != null && postulacion.getConcurso() != null) {
                return "la postulación de " + entre(etiqueta(postulacion.getDocente()))
                        + " al concurso " + entre(etiqueta(postulacion.getConcurso()));
            }
            return "la postulación " + entre(nombre);
        }
        if (entidad instanceof Alerta) {
            return "la alerta " + entre(nombre);
        }
        return "un registro " + entre(nombre);
    }

    /** Texto de la toma de posesión: quién y en qué concurso. */
    static String posesion(ConcursoDocente postulacion) {
        String docente = postulacion.getDocente() != null ? " de " + entre(etiqueta(postulacion.getDocente())) : "";
        String concurso = postulacion.getConcurso() != null
                ? " en el concurso " + entre(etiqueta(postulacion.getConcurso())) : "";
        return "Se confirmó la toma de posesión" + docente + concurso + ".";
    }

    /**
     * Lista de lo que cambió entre dos fotos de valores: «campo (de «A» a «B»); otro campo (...)».
     * Vacía si no hay diferencias en los datos registrados.
     */
    static String cambios(Map<String, Object> antes, Map<String, Object> despues) {
        // Se recorre la unión de campos: uno que estaba vacío y ahora tiene valor también es un cambio.
        Set<String> campos = new LinkedHashSet<>(antes.keySet());
        campos.addAll(despues.keySet());
        List<String> partes = new ArrayList<>();
        for (String campo : campos) {
            if (CAMPOS_OCULTOS.contains(campo)) {
                continue;
            }
            Object valorAntes = antes.get(campo);
            Object valorDespues = despues.get(campo);
            if (Objects.equals(valorAntes, valorDespues)) {
                continue;
            }
            String nombre = nombreCampo(campo);
            if (valorAntes == null) {
                partes.add(nombre + " (antes vacío, ahora " + entre(valor(valorDespues)) + ")");
            } else if (valorDespues == null) {
                partes.add(nombre + " (antes " + entre(valor(valorAntes)) + ", ahora vacío)");
            } else {
                partes.add(nombre + " (de " + entre(valor(valorAntes)) + " a " + entre(valor(valorDespues)) + ")");
            }
        }
        return String.join("; ", partes);
    }

    static String nombreCampo(String campo) {
        String conocido = CAMPOS.get(campo);
        if (conocido != null) {
            return conocido;
        }
        // Respaldo: "fechaAlgo" -> "fecha algo".
        return campo.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
    }

    /** Valor en lenguaje común: fechas dd/MM/yyyy, sí/no, estados con su nombre y no con el código interno. */
    static String valor(Object valor) {
        if (valor == null) {
            return "vacío";
        }
        if (valor instanceof Boolean booleano) {
            return booleano ? "sí" : "no";
        }
        if (valor instanceof LocalDate fecha) {
            return FECHA.format(fecha);
        }
        if (valor instanceof LocalDateTime fechaHora) {
            return FECHA_HORA.format(fechaHora);
        }
        if (valor instanceof EstadoPostulacion estado) {
            return ReglasConcurso.etiqueta(estado);
        }
        if (valor instanceof Enum<?> enumerado) {
            String texto = enumerado.name().replace('_', ' ').toLowerCase();
            return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
        }
        return abreviar(valor.toString().trim());
    }

    private static String nombreCompleto(Persona persona) {
        boolean hayApellido = esTexto(persona.getApellido());
        boolean hayNombre = esTexto(persona.getNombre());
        if (hayApellido && hayNombre) {
            return persona.getApellido().trim() + ", " + persona.getNombre().trim();
        }
        if (hayApellido) {
            return persona.getApellido().trim();
        }
        return hayNombre ? persona.getNombre().trim() : numero(persona);
    }

    private static String textoONulo(String texto, BaseEntity<?> entidad) {
        return esTexto(texto) ? texto.trim() : numero(entidad);
    }

    /** Último recurso cuando el registro no tiene nombre: su número interno (no es el uuid público). */
    private static String numero(BaseEntity<?> entidad) {
        return entidad.getId() == null ? "sin nombre" : "n.º " + entidad.getId();
    }

    private static boolean esTexto(String texto) {
        return texto != null && !texto.isBlank();
    }

    private static String abreviar(String texto) {
        return texto.length() <= LARGO_MAXIMO_VALOR ? texto : texto.substring(0, LARGO_MAXIMO_VALOR - 1) + "…";
    }

    private static String entre(String texto) {
        return "«" + texto + "»";
    }
}