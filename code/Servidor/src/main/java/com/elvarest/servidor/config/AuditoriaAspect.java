package com.elvarest.servidor.config;

import com.elvarest.servidor.entities.Auditoria;
import com.elvarest.servidor.entities.BaseEntity;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.enums.AccionAuditoria;
import com.elvarest.servidor.services.AuditoriaService;
import com.elvarest.servidor.services.BaseService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Auditoría transversal del CRUD: intercepta alta, modificar y bajaLogica de BaseService sin
 * tocar los servicios existentes. Registra quién (login autenticado), qué acción, sobre qué
 * entidad y uuid, con una frase breve en lenguaje común sobre el cambio. Las operaciones rechazadas (regla de
 * negocio) no generan evento; si el registro de auditoría falla, la operación ya aplicada no
 * se revierte (fail-open) y el evento queda en el log del Servidor.
 *
 * Detalle: cada evento lleva una frase en lenguaje común (qué registro es y qué se hizo; en las
 * modificaciones, el valor anterior y el actual de cada campo), redactada por {@link AuditoriaTextos}.
 *
 * Quién: el Cliente llama a la API con una cuenta de servicio ADMIN e informa en el header
 * {@value #HEADER_ACTOR} el usuario real de la sesión; ese es el que se registra.
 * Además del CRUD genérico se auditan los flujos que escriben por fuera de él: restablecer y
 * cambiar contraseña y la confirmación de toma de posesión.
 */
@Aspect
@Component
public class AuditoriaAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAspect.class);

    /** Header con el que el Cliente informa el usuario real de la sesión (la API se llama con una cuenta de servicio). */
    static final String HEADER_ACTOR = "X-Usuario-Actor";
    /** Largo de la columna Auditoria.usuario. */
    private static final int LARGO_MAXIMO_USUARIO = 50;
    /** Largo de la columna Auditoria.detalle: una frase más larga se recorta en lugar de perder el evento. */
    private static final int LARGO_MAXIMO_DETALLE = 1000;

    private final AuditoriaService auditoriaService;

    public AuditoriaAspect(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Around("(execution(* com.elvarest.servidor.services.BaseService+.alta(..)) || " +
            "execution(* com.elvarest.servidor.services.BaseService+.modificar(..)) || " +
            "execution(* com.elvarest.servidor.services.BaseService+.bajaLogica(..))) && " +
            "!target(com.elvarest.servidor.services.AuditoriaService)")
    public Object auditar(ProceedingJoinPoint joinPoint) throws Throwable {
        String metodo = joinPoint.getSignature().getName();
        // El estado previo se lee antes de proceder: permite el diff de la modificación y
        // conservar el uuid de la baja aunque la entidad quede eliminada después.
        Object previo = ("modificar".equals(metodo) || "bajaLogica".equals(metodo))
                ? leerPrevio(joinPoint)
                : null;
        // El previo que devuelve getOne puede ser la misma instancia que luego se modifica durante
        // proceed() (sesión de persistencia compartida): se copian sus valores ahora para que el diff
        // compare contra lo que había antes y no contra el resultado.
        Map<String, Object> fotoPrevia = ("modificar".equals(metodo) && previo != null)
                ? camposSimples(previo)
                : null;
        // Cómo se identificaba el registro antes del cambio (en una baja ya no se puede consultar).
        String frasePrevia = fraseDe(previo);

        Object resultado;
        try {
            resultado = joinPoint.proceed();
        } catch (Throwable e) {
            // Operación rechazada (regla de negocio) o con error: no hay evento que auditar.
            throw e;
        }

        try {
            if ("alta".equals(metodo)) {
                registrarAlta(resultado);
            } else if ("modificar".equals(metodo)) {
                registrarModificacion(fotoPrevia, frasePrevia, resultado);
            } else if ("bajaLogica".equals(metodo)) {
                registrarBaja(previo, frasePrevia, resultado);
            }
        } catch (Exception e) {
            // Fail-open: la operación ya se aplicó; el evento se conserva en el log del
            // Servidor con sus datos para recuperarlo manualmente.
            log.error("No se pudo registrar la auditoría de '{}' sobre '{}'. Usuario: {}",
                    metodo, joinPoint.getSignature().getDeclaringTypeName(), usuarioActual(), e);
        }
        return resultado;
    }

    /**
     * Flujos que escriben sin pasar por alta/modificar/bajaLogica: restablecer y cambiar contraseña
     * (Usuario) y confirmar la toma de posesión (postulación). Mismo criterio que el resto: solo se
     * registra si la operación tuvo éxito y un fallo del registro no revierte nada (fail-open).
     */
    @Around("execution(* com.elvarest.servidor.services.UsuarioService.restablecerContrase*a(..)) || " +
            "execution(* com.elvarest.servidor.services.UsuarioService.cambiarContrase*a(..)) || " +
            "execution(* com.elvarest.servidor.services.ConcursoDocenteService.confirmarPosesion(java.lang.String, java.lang.String, ..))")
    public Object auditarFlujo(ProceedingJoinPoint joinPoint) throws Throwable {
        String metodo = joinPoint.getSignature().getName();
        boolean posesion = "confirmarPosesion".equals(metodo);

        Object resultado = joinPoint.proceed();

        try {
            if (posesion) {
                registrarPosesion(joinPoint);
            } else {
                registrarCambioDeContrasena(resultado, metodo.startsWith("restablecer"));
            }
        } catch (Exception e) {
            log.error("No se pudo registrar la auditoría de '{}' sobre '{}'. Usuario: {}",
                    metodo, joinPoint.getSignature().getDeclaringTypeName(), usuarioActual(), e);
        }
        return resultado;
    }

    /** Lee la postulación por el uuid que recibe confirmarPosesion (segundo argumento). */
    @SuppressWarnings("unchecked")
    private BaseEntity<?> leerPostulacion(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length < 2 || !(args[1] instanceof String postulacionUuid)) {
            return null;
        }
        if (!(joinPoint.getTarget() instanceof BaseService<?, ?> servicio)) {
            return null;
        }
        try {
            return ((BaseService<BaseEntity<Long>, Long>) servicio).getOnePorUuid(postulacionUuid);
        } catch (Exception e) {
            return null;
        }
    }

    private void registrarPosesion(ProceedingJoinPoint joinPoint) {
        if (!(leerPostulacion(joinPoint) instanceof ConcursoDocente postulacion)) {
            return;
        }
        registrar(postulacion, AccionAuditoria.MODIFICACION, AuditoriaTextos.posesion(postulacion));
    }

    /** La contraseña nunca viaja al detalle: solo queda constancia de que se cambió y cómo. */
    private void registrarCambioDeContrasena(Object resultado, boolean restablecida) {
        if (!(resultado instanceof BaseEntity<?> usuario)) {
            return;
        }
        String nombre = "«" + AuditoriaTextos.etiqueta(usuario) + "»";
        String detalle = restablecida
                ? "Se restableció la contraseña del usuario " + nombre + "."
                : "El usuario " + nombre + " cambió su propia contraseña.";
        registrar(usuario, AccionAuditoria.MODIFICACION, detalle);
    }

    /** Lee el registro previo con getOne (público) antes de que proceda la operación. */
    @SuppressWarnings("unchecked")
    private Object leerPrevio(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof Long id)) {
            return null;
        }
        if (!(joinPoint.getTarget() instanceof BaseService<?, ?> servicio)) {
            return null;
        }
        try {
            return ((BaseService<BaseEntity<Long>, Long>) servicio).getOne(id);
        } catch (Exception e) {
            // El previo no se pudo leer (no existe o está eliminado): el evento se registra sin diff.
            return null;
        }
    }

    private void registrarAlta(Object resultado) {
        if (!(resultado instanceof BaseEntity<?> entidad)) {
            return;
        }
        registrar(entidad, AccionAuditoria.ALTA, "Se creó " + AuditoriaTextos.frase(entidad) + ".");
    }

    private void registrarModificacion(Map<String, Object> fotoPrevia, String frasePrevia, Object resultado) {
        if (!(resultado instanceof Optional<?> opcional) || opcional.isEmpty()) {
            return;
        }
        if (!(opcional.get() instanceof BaseEntity<?> entidad)) {
            return;
        }
        registrar(entidad, AccionAuditoria.MODIFICACION, detalleModificacion(fotoPrevia, frasePrevia, entidad));
    }

    private void registrarBaja(Object previo, String frasePrevia, Object resultado) {
        if (!(resultado instanceof Boolean dadoDeBaja) || !Boolean.TRUE.equals(dadoDeBaja)) {
            return;
        }
        if (!(previo instanceof BaseEntity<?> entidad)) {
            return;
        }
        registrar(entidad, AccionAuditoria.BAJA,
                "Se eliminó " + (frasePrevia != null ? frasePrevia : AuditoriaTextos.frase(entidad)) + ".");
    }

    private void registrar(BaseEntity<?> entidad, AccionAuditoria accion, String detalle) {
        Auditoria auditoria = new Auditoria();
        auditoria.setFechaHora(LocalDateTime.now());
        auditoria.setUsuario(usuarioActual());
        auditoria.setAccion(accion);
        auditoria.setEntidad(entidad.getClass().getSimpleName());
        auditoria.setRegistroUuid(entidad.getUuid());
        auditoria.setDetalle(acotar(detalle));
        auditoriaService.registrar(auditoria);
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = actorDeclarado(auth);
        if (actor != null) {
            return actor;
        }
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "SISTEMA";
        }
        return auth.getName();
    }

    /**
     * El Cliente llama a la API con una cuenta de servicio (ADMIN) e informa en un header el usuario
     * real de la sesión. Solo se acepta si quien llama es ADMIN: un usuario común que use la API
     * directamente no puede declararse como otro. Sin header (o sin petición HTTP) devuelve null.
     */
    private String actorDeclarado(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return null;
        }
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        String crudo = atributos.getRequest().getHeader(HEADER_ACTOR);
        if (crudo == null || crudo.isBlank()) {
            return null;
        }
        try {
            String actor = URLDecoder.decode(crudo, StandardCharsets.UTF_8).replaceAll("\\p{Cntrl}", "").trim();
            if (actor.isEmpty()) {
                return null;
            }
            return actor.length() > LARGO_MAXIMO_USUARIO ? actor.substring(0, LARGO_MAXIMO_USUARIO) : actor;
        } catch (IllegalArgumentException e) {
            // Codificación mal formada: se ignora el header y se usa la cuenta autenticada.
            return null;
        }
    }

    /** Frase de la modificación: qué registro era y, campo por campo, el valor anterior y el actual. */
    private String detalleModificacion(Map<String, Object> fotoPrevia, String frasePrevia, BaseEntity<?> entidad) {
        String frase = frasePrevia != null ? frasePrevia : AuditoriaTextos.frase(entidad);
        if (fotoPrevia == null) {
            return "Se modificó " + frase + ".";
        }
        String cambios = AuditoriaTextos.cambios(fotoPrevia, camposSimples(entidad));
        return cambios.isEmpty()
                ? "Se modificó " + frase + " (sin cambios en sus datos)."
                : "Se modificó " + frase + ". Cambios: " + cambios + ".";
    }

    /** Frase que identifica el registro, o null si no se pudo leer (el evento usa entonces el resultado). */
    private String fraseDe(Object entidad) {
        if (!(entidad instanceof BaseEntity<?> registro)) {
            return null;
        }
        try {
            return AuditoriaTextos.frase(registro);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String acotar(String detalle) {
        if (detalle == null || detalle.length() <= LARGO_MAXIMO_DETALLE) {
            return detalle;
        }
        return detalle.substring(0, LARGO_MAXIMO_DETALLE - 1) + "…";
    }

    /**
     * Recolecta por reflexión los getters de tipos simples. Una relación con otro registro (docente,
     * cargo, concurso...) se guarda como su nombre legible, para poder decir "de «A» a «B»"; las
     * colecciones quedan fuera.
     */
    private Map<String, Object> camposSimples(Object objeto) {
        Map<String, Object> campos = new LinkedHashMap<>();
        if (objeto == null) {
            return campos;
        }
        for (Method metodo : objeto.getClass().getMethods()) {
            if (!esGetter(metodo)) {
                continue;
            }
            Object valor;
            try {
                valor = metodo.invoke(objeto);
            } catch (Exception e) {
                continue;
            }
            if (valor == null) {
                continue;
            }
            if (esSimple(valor)) {
                campos.put(nombreCampo(metodo), valor);
            } else if (valor instanceof BaseEntity<?> relacion) {
                try {
                    campos.put(nombreCampo(metodo), AuditoriaTextos.etiqueta(relacion));
                } catch (RuntimeException e) {
                    // Relación no accesible (por ejemplo, sin sesión de persistencia): se omite del detalle.
                }
            }
        }
        return campos;
    }

    private static boolean esGetter(Method metodo) {
        if (metodo.getParameterCount() != 0 || metodo.getReturnType().equals(void.class)) {
            return false;
        }
        String nombre = metodo.getName();
        // Id interno, uuid (redundante con registroUuid), flag de baja y contraseña no viajan al detalle.
        if (nombre.equals("getClass") || nombre.equals("getId") || nombre.equals("getUuid")
                || nombre.equals("getEliminado") || nombre.equals("getContraseña")) {
            return false;
        }
        return (nombre.startsWith("get") && nombre.length() > 3)
                || (nombre.startsWith("is") && nombre.length() > 2);
    }

    private static String nombreCampo(Method metodo) {
        String nombre = metodo.getName();
        int inicio = nombre.startsWith("is") ? 2 : 3;
        String campo = nombre.substring(inicio);
        return Character.toLowerCase(campo.charAt(0)) + campo.substring(1);
    }

    private static boolean esSimple(Object valor) {
        return valor instanceof String || valor instanceof Number || valor instanceof Boolean
                || valor instanceof Character || valor instanceof Enum || valor instanceof UUID
                || valor instanceof Temporal;
    }
}