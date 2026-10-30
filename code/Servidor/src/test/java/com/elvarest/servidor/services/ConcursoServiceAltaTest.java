package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.enums.CaracterDesignacion;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de alta de concurso (§3.6). El alta crea el concurso completo, sin depender de lo que haya
 * validado el formulario:
 * - nace PUBLICADO (el estado no se elige) y con fecha de apertura de hoy si no viene;
 * - exige descripción, cargo, fecha del concurso y carácter de la designación;
 * - las horas totales se persisten: si no vienen se toman de la carga horaria del cargo.
 * El cargo, la fecha y las horas deben estar completos porque ellos definen las tomas de horas y la
 * designación que después se emite desde el concurso.
 */
@ExtendWith(MockitoExtension.class)
class ConcursoServiceAltaTest {

    private static final String MENSAJE_SIN_DESCRIPCION = "La descripción del concurso es obligatoria.";
    private static final String MENSAJE_SIN_CARGO = "Seleccione el cargo vacante del concurso.";
    private static final String MENSAJE_SIN_FECHA = "Indique la fecha y hora del concurso.";
    private static final String MENSAJE_SIN_CARACTER =
            "Seleccione la designación (carácter de designación) del concurso.";
    private static final String MENSAJE_SIN_HORAS = "El concurso no tiene horas totales: indique las horas "
            + "totales o complete la carga horaria del cargo.";

    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private DesignacionRepository designacionRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;
    @Mock
    private AlertaService alertaService;
    @Mock
    private CargoRepository cargoRepository;

    private ConcursoService service;

    @BeforeEach
    void setUp() {
        service = new ConcursoService(concursoRepository, designacionRepository, concursoDocenteRepository,
                alertaService, cargoRepository);
    }

    /** Pedido de alta con los datos del formulario: sin estado, fecha de apertura ni horas. */
    private Concurso pedido() {
        Concurso concurso = new Concurso();
        concurso.setDescripcion("Matemática 2026");
        Cargo cargo = new Cargo();
        cargo.setId(5L);
        concurso.setCargo(cargo);
        concurso.setFechaCierre(LocalDate.now().plusDays(10));
        concurso.setCaracterDesignacion(CaracterDesignacion.SUPLENTE);
        return concurso;
    }

    /** Cargo guardado, tal como lo lee el alta para validar y respaldar las horas. */
    private void conCargoGuardado(int cargaHoraria) {
        Cargo cargo = new Cargo();
        cargo.setId(5L);
        cargo.setCargaHoraria(cargaHoraria);
        lenient().when(cargoRepository.findById(5L)).thenReturn(Optional.of(cargo));
    }

    private void conAltaPersistible() {
        when(concursoRepository.findAll()).thenReturn(List.of());
        when(concursoRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void altaCompletaNacePublicadaConAperturaDeHoyYHorasDelCargo() throws ErrorServiceException {
        conCargoGuardado(10);
        conAltaPersistible();
        Concurso pedido = pedido();

        Concurso guardado = service.alta(pedido);

        assertEquals(EstadoConcurso.PUBLICADO, guardado.getEstadoConcurso());
        assertEquals(LocalDate.now(), guardado.getFechaApertura());
        // Las horas quedan persistidas con el concurso, no derivadas del cargo en cada lectura.
        assertEquals(10, guardado.getHorasTotales());
        verify(concursoRepository).save(pedido);
    }

    @Test
    void altaQueEnviaOtroEstadoNacePublicadaIgual() throws ErrorServiceException {
        conCargoGuardado(10);
        conAltaPersistible();
        Concurso pedido = pedido();
        pedido.setEstadoConcurso(EstadoConcurso.EN_PROCESO);

        assertEquals(EstadoConcurso.PUBLICADO, service.alta(pedido).getEstadoConcurso());
    }

    @Test
    void altaConHorasDelFormularioRespetaLasHorasElegidas() throws ErrorServiceException {
        conCargoGuardado(10);
        conAltaPersistible();
        Concurso pedido = pedido();
        pedido.setHorasTotales(6);

        assertEquals(6, service.alta(pedido).getHorasTotales());
    }

    @Test
    void altaSinDescripcionSeRechaza() {
        conCargoGuardado(10);
        Concurso pedido = pedido();
        pedido.setDescripcion("  ");

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedido));
        assertEquals(MENSAJE_SIN_DESCRIPCION, ex.getMessage());
    }

    @Test
    void altaSinCargoSeRechaza() {
        Concurso pedido = pedido();
        pedido.setCargo(null);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedido));
        assertEquals(MENSAJE_SIN_CARGO, ex.getMessage());
    }

    @Test
    void altaSinFechaDelConcursoSeRechaza() {
        conCargoGuardado(10);
        Concurso pedido = pedido();
        pedido.setFechaCierre(null);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedido));
        assertEquals(MENSAJE_SIN_FECHA, ex.getMessage());
    }

    @Test
    void altaSinCaracterDeDesignacionSeRechaza() {
        conCargoGuardado(10);
        Concurso pedido = pedido();
        pedido.setCaracterDesignacion(null);

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedido));
        assertEquals(MENSAJE_SIN_CARACTER, ex.getMessage());
    }

    @Test
    void altaSinHorasNiCargaHorariaDelCargoSeRechaza() {
        // Sin horas no hay toma de horas posible: se rechaza en lugar de guardar un concurso vacío.
        conCargoGuardado(0);
        Concurso pedido = pedido();

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.alta(pedido));
        assertEquals(MENSAJE_SIN_HORAS, ex.getMessage());
    }
}
