package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Designacion;
import com.elvarest.servidor.entities.enums.CaracterCertificado;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoDesignacion;
import com.elvarest.servidor.entities.enums.PrioridadAlerta;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.AlertaRepository;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Alertas automáticas del sistema. Hay tres orígenes y todos usan la misma entidad {@link Alerta}
 * (mismo formato en Alertas y en el dashboard):
 * - certificados provisorios por vencer (3 días o menos -> ALTA; 7 o menos -> MEDIA);
 * - designaciones por terminar (0 a 10 días -> ALTA; 11 a 15 -> MEDIA; vencidas -> ALTA);
 * - concursos en proceso sin cerrarse (3 días o más -> MEDIA; 7 o más -> ALTA) y concursos
 *   cerrados con horas sin cubrir (MEDIA).
 * Cada causa tiene una sola alerta: al cambiar el umbral se actualizan la prioridad y el mensaje,
 * no se crea otra. Una alerta leída se respeta mientras la gravedad no cambie; vuelve a pendiente
 * solo si el aviso escala (MEDIA -> ALTA) y nunca se resuelve una alerta manual.
 */
@Service
public class AlertaService extends BaseService<Alerta, Long> {

    /** Certificados: faltando 7 días o menos -> MEDIA. */
    public static final int DIAS_ALERTA_MEDIA = 7;
    /** Certificados: faltando 3 días o menos -> ALTA. */
    public static final int DIAS_ALERTA_ALTA = 3;

    /** Designaciones: faltando 10 días o menos -> ALTA (incluye las ya vencidas). */
    public static final int DIAS_DESIGNACION_ALTA = 10;
    /** Designaciones: faltando 15 días o menos -> MEDIA. */
    public static final int DIAS_DESIGNACION_MEDIA = 15;

    /** Concursos: 3 días o más desde el cierre sin cerrarse -> MEDIA. */
    public static final int DIAS_CONCURSO_MEDIA = 3;
    /** Concursos: 7 días o más desde el cierre sin cerrarse -> ALTA. */
    public static final int DIAS_CONCURSO_ALTA = 7;

    /** Origen de la alerta de concurso que sigue abierto después de su fecha de cierre. */
    public static final String TIPO_CONCURSO_SIN_CERRAR = "CONCURSO_SIN_CERRAR";
    /** Origen de la alerta de concurso cerrado con horas sin cubrir. */
    public static final String TIPO_CONCURSO_HORAS_SIN_CUBRIR = "CONCURSO_HORAS_SIN_CUBRIR";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AlertaRepository alertaRepository;
    private final CertificadoRepository certificadoRepository;
    private final DesignacionRepository designacionRepository;
    private final ConcursoRepository concursoRepository;
    private final ConcursoDocenteRepository concursoDocenteRepository;
    private final CargoRepository cargoRepository;

    public AlertaService(AlertaRepository repository,
                         CertificadoRepository certificadoRepository,
                         DesignacionRepository designacionRepository,
                         ConcursoRepository concursoRepository,
                         ConcursoDocenteRepository concursoDocenteRepository,
                         CargoRepository cargoRepository) {
        super(repository);
        this.alertaRepository = repository;
        this.certificadoRepository = certificadoRepository;
        this.designacionRepository = designacionRepository;
        this.concursoRepository = concursoRepository;
        this.concursoDocenteRepository = concursoDocenteRepository;
        this.cargoRepository = cargoRepository;
    }

    /** Una alerta manual puede nacer leída: la fecha de resolución la registra el Servidor. */
    @Override
    protected void preAlta(Alerta alerta) throws ErrorServiceException {
        alerta.setFechaResolucion(alerta.isLeida() ? LocalDate.now() : null);
    }

    /** Los vínculos de origen no se cambian desde la API (p. ej. al marcar la alerta como leída). */
    @Override
    protected void preModificacion(Alerta alerta) throws ErrorServiceException {
        alertaRepository.findById(alerta.getId())
                .ifPresent(guardada -> {
                    alerta.setCertificado(guardada.getCertificado());
                    alerta.setConcursoId(guardada.getConcursoId());
                    alerta.setTipo(guardada.getTipo());
                    alerta.setFechaResolucion(resolucionDe(alerta, guardada));
                });
    }

    /**
     * La fecha de resolución la fija el Servidor (el formulario no la envía): se registra al pasar
     * a leída, se conserva mientras siga leída y se limpia si vuelve a pendiente.
     */
    private LocalDate resolucionDe(Alerta nueva, Alerta guardada) {
        if (!nueva.isLeida()) {
            return null;
        }
        return guardada.isLeida() ? guardada.getFechaResolucion() : LocalDate.now();
    }

    // ------------------------------------------------------------------
    // Certificados provisorios por vencer
    // ------------------------------------------------------------------

    /** Revisa todos los certificados provisorios actuales. Lo invoca el scheduler diario. */
    @Transactional
    public int generarAlertasDeVencimiento() {
        int creadas = 0;
        for (Certificado certificado
                : certificadoRepository.findByCaracterAndActualTrueAndEliminadoFalse(CaracterCertificado.PROVISORIO)) {
            if (evaluarVencimiento(certificado)) {
                creadas++;
            }
        }
        return creadas;
    }

    /**
     * Deja la alerta que corresponda según los días que faltan para el vencimiento: 3 días o menos
     * -> ALTA (también si ya venció); 7 días o menos -> MEDIA. Si el certificado ya no la necesita,
     * la alerta existente se marca leída. No alerta certificados reemplazados, definitivos ni de
     * postulaciones canceladas.
     *
     * @return true si la alerta quedó pendiente como consecuencia de este cambio (alta o escalada).
     */
    public boolean evaluarVencimiento(Certificado certificado) {
        if (certificado == null || certificado.getId() == null) {
            return false;
        }
        PrioridadAlerta prioridad = prioridadCertificado(certificado);
        List<Alerta> alertas = alertaRepository.findByCertificadoId(certificado.getId());
        if (prioridad == null) {
            resolverTodas(alertas);
            return false;
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), certificado.getFechaVencimiento());
        String texto = mensaje(certificado, dias);
        return aplicarAlerta(alertas, prioridad, texto,
                () -> crearAlertaCertificado(certificado, prioridad, texto));
    }

    private void crearAlertaCertificado(Certificado certificado, PrioridadAlerta prioridad, String mensaje) {
        Alerta alerta = nuevaAlerta(prioridad, mensaje);
        alerta.setCertificado(certificado);
        alertaRepository.save(alerta);
    }

    /** Condiciones para que un certificado siga necesitando alerta de vencimiento. */
    private boolean certificadoVigente(Certificado certificado) {
        if (certificado == null
                || certificado.getCaracter() != CaracterCertificado.PROVISORIO
                || !certificado.isActual()
                || Boolean.TRUE.equals(certificado.getEliminado())
                || certificado.getFechaVencimiento() == null) {
            return false;
        }
        ConcursoDocente postulacion = certificado.getConcursoDocente();
        return postulacion == null || !ReglasConcurso.esCancelada(postulacion);
    }

    /** 3 días o menos -> ALTA; 7 días o menos -> MEDIA; si falta más, no corresponde alerta. */
    private PrioridadAlerta prioridadCertificado(Certificado certificado) {
        if (!certificadoVigente(certificado)) {
            return null;
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), certificado.getFechaVencimiento());
        return dias <= DIAS_ALERTA_ALTA ? PrioridadAlerta.ALTA
                : dias <= DIAS_ALERTA_MEDIA ? PrioridadAlerta.MEDIA
                : null;
    }

    /**
     * Ej.: "El apto psicofísico provisorio de Pérez, Juan (Concurso Matemática 1° año) vence el
     * 25/10/2026." El mismo día del vencimiento agrega " (hoy)", como las designaciones.
     */
    private String mensaje(Certificado certificado, long dias) {
        String tipo = certificado.getTipoCertificado() != null && certificado.getTipoCertificado().getNombre() != null
                ? certificado.getTipoCertificado().getNombre().toLowerCase(Locale.forLanguageTag("es"))
                : "certificado";
        ConcursoDocente postulacion = certificado.getConcursoDocente();
        String docente = postulacion != null && postulacion.getDocente() != null
                ? postulacion.getDocente().getApellido() + ", " + postulacion.getDocente().getNombre()
                : "docente sin datos";
        String concurso = postulacion != null && postulacion.getConcurso() != null
                ? (postulacion.getConcurso().getDescripcion() != null
                ? postulacion.getConcurso().getDescripcion()
                : "#" + postulacion.getConcurso().getId())
                : "sin datos";
        String verbo = dias < 0 ? "venció el " : "vence el ";
        String detalle = dias == 0 ? " (hoy)" : "";
        return "El " + tipo + " provisorio de " + docente + " (Concurso " + concurso + ")"
                + verbo + certificado.getFechaVencimiento().format(FORMATO_FECHA) + detalle + ".";
    }

    // ------------------------------------------------------------------
    // Designaciones por terminar
    // ------------------------------------------------------------------

    /** Revisa todas las designaciones activas con fecha de fin definitiva. */
    @Transactional
    public int generarAlertasDeDesignaciones() {
        int creadas = 0;
        for (Designacion designacion : designacionRepository.findAll()) {
            if (evaluarVencimientoDesignacion(designacion)) {
                creadas++;
            }
        }
        return creadas;
    }

    /**
     * Evalúa el fin de una designación: 0 a 10 días -> ALTA (también si ya venció y sigue activa);
     * 11 a 15 -> MEDIA. Cada designación tiene una sola alerta: si la prioridad escala, vuelve a
     * pendiente; si la fecha de fin sale de rango, la alerta se marca leída. No alerta
     * designaciones finalizadas, eliminadas ni sin fecha de fin.
     *
     * @return true si la alerta quedó pendiente como consecuencia de este cambio (alta o escalada).
     */
    @Transactional
    public boolean evaluarVencimientoDesignacion(Designacion origen) {
        if (origen == null || origen.getId() == null) {
            return false;
        }
        Designacion designacion = designacionRepository.findById(origen.getId()).orElse(null);
        if (designacion == null) {
            return false;
        }
        boolean vigente = designacionVigente(designacion);
        long dias = vigente ? ChronoUnit.DAYS.between(LocalDate.now(), designacion.getFechaFinDefinitiva()) : 0;
        PrioridadAlerta prioridad = vigente ? prioridadDesignacion(dias) : null;
        List<Alerta> alertas = alertasDe(designacion);

        if (prioridad == null) {
            resolverTodas(alertas);
            return false;
        }
        String texto = mensajeDesignacion(designacion, dias);
        return aplicarAlerta(alertas, prioridad, texto,
                () -> crearAlertaDesignacion(designacion, prioridad, texto));
    }

    private void crearAlertaDesignacion(Designacion designacion, PrioridadAlerta prioridad, String mensaje) {
        Alerta alerta = nuevaAlerta(prioridad, mensaje);
        alertaRepository.save(alerta);
        // El vínculo alerta -> designación lo administra la colección de la designación.
        alertasDe(designacion).add(alerta);
        designacionRepository.save(designacion);
    }

    /** Una designación sigue necesitando alerta si está activa, no eliminada y tiene fecha de fin. */
    private boolean designacionVigente(Designacion designacion) {
        return !Boolean.TRUE.equals(designacion.getEliminado())
                && designacion.getEstadoDesignacion() == EstadoDesignacion.ACTIVO
                && designacion.getFechaFinDefinitiva() != null;
    }

    /** 10 días o menos -> ALTA (incluye vencidas); 15 días o menos -> MEDIA. */
    private PrioridadAlerta prioridadDesignacion(long dias) {
        return dias <= DIAS_DESIGNACION_ALTA ? PrioridadAlerta.ALTA
                : dias <= DIAS_DESIGNACION_MEDIA ? PrioridadAlerta.MEDIA
                : null;
    }

    /** Ej.: "La designación de Pérez, Juan (Cargo: Profesor de Matemática · 1° Año) finaliza el 05/11/2026 (faltan 4 días)." */
    private String mensajeDesignacion(Designacion designacion, long dias) {
        String docente = designacion.getDocente() != null
                ? designacion.getDocente().getApellido() + ", " + designacion.getDocente().getNombre()
                : "docente sin datos";
        String cargo = descripcionCargoDe(designacion);
        String verbo = dias < 0 ? "finalizó el " : "finaliza el ";
        String detalle = dias < 0
                ? " (hace " + Math.abs(dias) + (Math.abs(dias) == 1 ? " día" : " días") + ")"
                : dias == 0 ? " (hoy)"
                : " (faltan " + dias + (dias == 1 ? " día" : " días") + ")";
        return "La designación de " + docente + (cargo != null ? " (" + cargo + ")" : "")
                + " " + verbo + designacion.getFechaFinDefinitiva().format(FORMATO_FECHA) + detalle + ".";
    }

    /** Cargo al que pertenece la designación (vínculo unidireccional Cargo -> designaciones). */
    private String descripcionCargoDe(Designacion designacion) {
        if (designacion.getId() == null) {
            return null;
        }
        for (Cargo cargo : cargoRepository.findAll()) {
            if (Boolean.TRUE.equals(cargo.getEliminado()) || cargo.getDesignaciones() == null) {
                continue;
            }
            boolean pertenece = cargo.getDesignaciones().stream()
                    .anyMatch(otra -> otra != null && Objects.equals(otra.getId(), designacion.getId()));
            if (pertenece) {
                String descripcion = cargo.getDescripcion() != null && !cargo.getDescripcion().isBlank()
                        ? cargo.getDescripcion()
                        : "Cargo #" + cargo.getId();
                String materia = cargo.getMateria() != null && cargo.getMateria().getNombre() != null
                        ? " · " + cargo.getMateria().getNombre()
                        : "";
                return "Cargo: " + descripcion + materia;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Concursos en proceso sin cerrar
    // ------------------------------------------------------------------

    /** Revisa todos los concursos en proceso (estado efectivo) con fecha de cierre. */
    @Transactional
    public int generarAlertasDeConcursos() {
        int creadas = 0;
        for (Concurso concurso : concursoRepository.findAll()) {
            if (evaluarConcursoSinCerrar(concurso)) {
                creadas++;
            }
        }
        return creadas;
    }

    /**
     * Evalúa las alertas de un concurso después de un cierre o una edición: «sigue en proceso»
     * (3 días o más -> MEDIA; 7 o más -> ALTA) y «quedó cerrado con horas sin cubrir» (MEDIA).
     * Cada una es una alerta propia (las distingue {@code tipo}); si la prioridad escala, la
     * alerta vuelve a pendiente.
     *
     * @return true si alguna alerta quedó pendiente por este cambio (alta nueva o escalada).
     */
    @Transactional
    public boolean evaluarConcursoSinCerrar(Concurso origen) {
        if (origen == null || origen.getId() == null) {
            return false;
        }
        Concurso concurso = concursoRepository.findById(origen.getId()).orElse(null);
        if (concurso == null) {
            return false;
        }
        boolean enProceso = concursoEnProceso(concurso);
        long dias = enProceso ? ChronoUnit.DAYS.between(concurso.getFechaCierre(), LocalDate.now()) : 0;
        PrioridadAlerta prioridad = enProceso ? prioridadConcurso(dias) : null;
        List<Alerta> alertas = alertasDeConcurso(concurso, TIPO_CONCURSO_SIN_CERRAR);

        boolean creada = false;
        if (prioridad == null) {
            resolverTodas(alertas);
        } else {
            String texto = mensajeConcurso(concurso, dias);
            creada = aplicarAlerta(alertas, prioridad, texto,
                    () -> crearAlertaConcurso(concurso, TIPO_CONCURSO_SIN_CERRAR, prioridad, texto));
        }
        // Un concurso cerrado puede tener horas sin cubrir: es otra alerta, con su propio tipo.
        return evaluarHorasSinCubrir(concurso) || creada;
    }

    /**
     * Estado efectivo: EN_PROCESO o PUBLICADO cuya fecha de cierre ya pasó. La transición la
     * persiste el Cliente al abrir el listado; sin esto, un concurso vencido que nadie consultó
     * nunca generaría la alerta.
     */
    private boolean concursoEnProceso(Concurso concurso) {
        if (Boolean.TRUE.equals(concurso.getEliminado()) || concurso.getFechaCierre() == null) {
            return false;
        }
        if (concurso.getEstadoConcurso() == EstadoConcurso.EN_PROCESO) {
            return true;
        }
        return concurso.getEstadoConcurso() == EstadoConcurso.PUBLICADO
                && !concurso.getFechaCierre().isAfter(LocalDate.now());
    }

    /** 3 días o más -> MEDIA; 7 días o más -> ALTA. */
    private PrioridadAlerta prioridadConcurso(long dias) {
        return dias >= DIAS_CONCURSO_ALTA ? PrioridadAlerta.ALTA
                : dias >= DIAS_CONCURSO_MEDIA ? PrioridadAlerta.MEDIA
                : null;
    }

    /** Ej.: "El concurso «Matemática 1° año» sigue en proceso: la fecha de cierre fue el 19/10/2026 (hace 4 días)." */
    private String mensajeConcurso(Concurso concurso, long dias) {
        return "El concurso «" + descripcionDe(concurso) + "» sigue en proceso: la fecha de cierre fue el "
                + concurso.getFechaCierre().format(FORMATO_FECHA)
                + " (hace " + dias + (dias == 1 ? " día" : " días") + ").";
    }

    // ------------------------------------------------------------------
    // Concurso cerrado con horas sin cubrir
    // ------------------------------------------------------------------

    /**
     * Alerta MEDIA para un concurso cerrado con horas que no tienen designación ni se
     * transfirieron a un rellamado. Se resuelve sola cuando esas horas se cubren o se transfieren,
     * o cuando el concurso se elimina o se reabre.
     */
    private boolean evaluarHorasSinCubrir(Concurso concurso) {
        Integer disponibles = horasSinCubrir(concurso);
        List<Alerta> alertas = alertasDeConcurso(concurso, TIPO_CONCURSO_HORAS_SIN_CUBRIR);
        if (disponibles == null || disponibles <= 0) {
            resolverTodas(alertas);
            return false;
        }
        String texto = mensajeHorasSinCubrir(concurso, disponibles);
        return aplicarAlerta(alertas, PrioridadAlerta.MEDIA, texto,
                () -> crearAlertaConcurso(concurso, TIPO_CONCURSO_HORAS_SIN_CUBRIR, PrioridadAlerta.MEDIA, texto));
    }

    /**
     * Horas del concurso cerrado que quedaron libres: total − horas tomadas vigentes − transferidas
     * a un rellamado. Devuelve null si el concurso no está cerrado o no se puede calcular el total.
     */
    private Integer horasSinCubrir(Concurso concurso) {
        if (Boolean.TRUE.equals(concurso.getEliminado())
                || concurso.getEstadoConcurso() != EstadoConcurso.FINALIZADO
                || concurso.getId() == null) {
            return null;
        }
        return ReglasConcurso.horasDisponibles(concurso,
                concursoDocenteRepository.findByConcursoIdOrderByIdAsc(concurso.getId()));
    }

    private String mensajeHorasSinCubrir(Concurso concurso, int disponibles) {
        return "El concurso «" + descripcionDe(concurso) + "» quedó cerrado con " + disponibles
                + (disponibles == 1 ? " hora sin cubrir." : " horas sin cubrir.");
    }

    private void crearAlertaConcurso(Concurso concurso, String tipo, PrioridadAlerta prioridad, String mensaje) {
        Alerta alerta = nuevaAlerta(prioridad, mensaje);
        alerta.setConcursoId(concurso.getId());
        alerta.setTipo(tipo);
        alertaRepository.save(alerta);
    }

    private String descripcionDe(Concurso concurso) {
        return concurso.getDescripcion() != null && !concurso.getDescripcion().isBlank()
                ? concurso.getDescripcion()
                : "#" + concurso.getId();
    }

    // ------------------------------------------------------------------
    // Una alerta por causa: altas, actualización y resolución
    // ------------------------------------------------------------------

    /**
     * Aplica la alerta de una causa: si no existe la crea; si existe, actualiza prioridad y mensaje.
     * Vuelve a pendiente solo cuando la prioridad escala (MEDIA -> ALTA), porque un aviso más grave
     * debe verse aunque el usuario hubiera leído o dado de baja el anterior.
     *
     * @return true si la alerta quedó pendiente por este cambio (alta nueva o escalada).
     */
    private boolean aplicarAlerta(List<Alerta> alertas, PrioridadAlerta prioridad, String mensaje, Runnable alta) {
        if (alertas.isEmpty()) {
            alta.run();
            return true;
        }
        return actualizarAlerta(alertas, prioridad, mensaje);
    }

    private boolean actualizarAlerta(List<Alerta> alertas, PrioridadAlerta prioridad, String mensaje) {
        Alerta existente = principalDe(alertas);
        consolidar(alertas, existente);
        boolean cambioPrioridad = existente.getPrioridad() != prioridad;
        if (!cambioPrioridad && Objects.equals(existente.getMensaje(), mensaje)) {
            return false;
        }
        boolean escala = cambioPrioridad && esMasGrave(prioridad, existente.getPrioridad());
        // Una baja manual se respeta: solo un aviso más grave la reactiva.
        if (Boolean.TRUE.equals(existente.getEliminado()) && !escala) {
            return false;
        }
        existente.setPrioridad(prioridad);
        existente.setMensaje(mensaje);
        if (escala) {
            existente.setLeida(false);
            existente.setFechaResolucion(null);
            existente.setEliminado(false);
            existente.setFechaEmision(LocalDate.now());
        }
        alertaRepository.save(existente);
        return escala;
    }

    /**
     * Alerta que representa la causa: la no eliminada de mayor gravedad (si todas están dadas de
     * baja, la primera). Las alertas previas a este esquema podían tener dos registros por causa
     * (MEDIA y ALTA); se consolidan en una sola sin borrar el historial.
     */
    private Alerta principalDe(List<Alerta> alertas) {
        return alertas.stream()
                .filter(alerta -> !Boolean.TRUE.equals(alerta.getEliminado()))
                .max(Comparator.comparingInt(alerta -> gravedad(alerta.getPrioridad())))
                .orElseGet(() -> alertas.get(0));
    }

    /** Marca leídas las alertas duplicadas de la causa. */
    private void consolidar(List<Alerta> alertas, Alerta principal) {
        for (Alerta alerta : alertas) {
            if (alerta == principal || Boolean.TRUE.equals(alerta.getEliminado()) || alerta.isLeida()) {
                continue;
            }
            alerta.setLeida(true);
            alerta.setFechaResolucion(LocalDate.now());
            alertaRepository.save(alerta);
        }
    }

    /** Marca leídas las alertas de una causa que ya no necesita aviso (respeta las dadas de baja). */
    private void resolverTodas(List<Alerta> alertas) {
        for (Alerta alerta : alertas) {
            if (Boolean.TRUE.equals(alerta.getEliminado()) || alerta.isLeida()) {
                continue;
            }
            alerta.setLeida(true);
            alerta.setFechaResolucion(LocalDate.now());
            alertaRepository.save(alerta);
        }
    }

    private boolean esMasGrave(PrioridadAlerta nueva, PrioridadAlerta anterior) {
        return anterior == null || gravedad(nueva) > gravedad(anterior);
    }

    /** ALTA es más grave que MEDIA (el orden declarado del enum no lo refleja). */
    private int gravedad(PrioridadAlerta prioridad) {
        return prioridad == PrioridadAlerta.ALTA ? 1 : 0;
    }

    private Alerta nuevaAlerta(PrioridadAlerta prioridad, String mensaje) {
        Alerta alerta = new Alerta();
        alerta.setMensaje(mensaje);
        alerta.setFechaEmision(LocalDate.now());
        alerta.setLeida(false);
        alerta.setPrioridad(prioridad);
        alerta.setEliminado(false);
        return alerta;
    }

    private List<Alerta> alertasDe(Designacion designacion) {
        if (designacion.getAlertas() == null) {
            designacion.setAlertas(new ArrayList<>());
        }
        return designacion.getAlertas();
    }

    /** Alertas del concurso del tipo indicado; en «sigue en proceso» incluye las anteriores a la columna tipo. */
    private List<Alerta> alertasDeConcurso(Concurso concurso, String tipo) {
        List<Alerta> alertas = alertaRepository.findByConcursoId(concurso.getId());
        if (alertas == null || alertas.isEmpty()) {
            return List.of();
        }
        if (TIPO_CONCURSO_SIN_CERRAR.equals(tipo)) {
            return alertas.stream()
                    .filter(alerta -> alerta.getTipo() == null || TIPO_CONCURSO_SIN_CERRAR.equals(alerta.getTipo()))
                    .toList();
        }
        return alertas.stream()
                .filter(alerta -> tipo.equals(alerta.getTipo()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Depuración: alertas cuya causa ya desapareció
    // ------------------------------------------------------------------

    /**
     * Marca leídas las alertas automáticas que quedaron sin causa: el certificado se renovó o se
     * dio de baja, la designación terminó, el concurso se cerró o eliminó, o las horas se
     * cubrieron. Los cambios de prioridad no se resuelven acá: los actualiza la evaluación de la
     * causa. Nunca toca las alertas manuales ni las dadas de baja.
     *
     * @return cantidad de alertas marcadas como leídas.
     */
    @Transactional
    public int depurarAlertasObsoletas() {
        Map<Long, Designacion> designaciones = designacionesPorAlerta();
        int resueltas = 0;
        for (Alerta alerta : alertaRepository.findAll()) {
            if (Boolean.TRUE.equals(alerta.getEliminado()) || alerta.isLeida()) {
                continue;
            }
            if (obsoleta(alerta, designaciones) && marcarLeida(alerta)) {
                resueltas++;
            }
        }
        return resueltas;
    }

    private boolean obsoleta(Alerta alerta, Map<Long, Designacion> designaciones) {
        if (alerta.getCertificado() != null) {
            // Si sigue vigente, evaluarVencimiento actualiza su prioridad; acá solo importa la causa.
            return prioridadCertificado(alerta.getCertificado()) == null;
        }
        if (alerta.getConcursoId() != null) {
            return TIPO_CONCURSO_HORAS_SIN_CUBRIR.equals(alerta.getTipo())
                    ? obsoletaPorHoras(alerta)
                    : obsoletaPorConcurso(alerta);
        }
        Designacion designacion = alerta.getId() != null ? designaciones.get(alerta.getId()) : null;
        return designacion != null && !designacionVigente(designacion);
    }

    private boolean obsoletaPorConcurso(Alerta alerta) {
        Concurso concurso = concursoRepository.findById(alerta.getConcursoId()).orElse(null);
        return concurso == null || !concursoEnProceso(concurso);
    }

    private boolean obsoletaPorHoras(Alerta alerta) {
        Concurso concurso = concursoRepository.findById(alerta.getConcursoId()).orElse(null);
        if (concurso == null) {
            return true;
        }
        Integer disponibles = horasSinCubrir(concurso);
        return disponibles == null || disponibles <= 0;
    }

    private boolean marcarLeida(Alerta alerta) {
        alerta.setLeida(true);
        alerta.setFechaResolucion(LocalDate.now());
        alertaRepository.save(alerta);
        return true;
    }

    /** Alertas de designación indexadas por id (el vínculo vive en la colección de la designación). */
    private Map<Long, Designacion> designacionesPorAlerta() {
        Map<Long, Designacion> mapa = new HashMap<>();
        for (Designacion designacion : designacionRepository.findAll()) {
            if (designacion.getAlertas() == null) {
                continue;
            }
            for (Alerta alerta : designacion.getAlertas()) {
                if (alerta != null && alerta.getId() != null) {
                    mapa.put(alerta.getId(), designacion);
                }
            }
        }
        return mapa;
    }
}
