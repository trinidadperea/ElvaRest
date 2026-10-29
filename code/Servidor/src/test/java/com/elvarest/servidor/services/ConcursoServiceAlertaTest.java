package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.repositories.CargoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.DesignacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El cierre del concurso (manual o automático) se persiste con ConcursoService.modificar: la alerta
 * de "sigue en proceso" tiene que resolverse en ese momento y no esperar a la corrida diaria.
 */
@ExtendWith(MockitoExtension.class)
class ConcursoServiceAlertaTest {

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

    private Concurso concurso(EstadoConcurso estado) {
        Concurso concurso = new Concurso();
        concurso.setId(1L);
        concurso.setEstadoConcurso(estado);
        return concurso;
    }

    @Test
    void alCerrarElConcursoSeReevaluanLasAlertas() throws Exception {
        when(concursoRepository.findById(1L))
                .thenReturn(Optional.of(concurso(EstadoConcurso.EN_PROCESO)));
        when(concursoRepository.save(any(Concurso.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        Concurso cerrado = concurso(EstadoConcurso.FINALIZADO);

        service.modificar(1L, cerrado);

        verify(alertaService).evaluarConcursoSinCerrar(cerrado);
    }

    @Test
    void modificacionDeUnConcursoInexistenteNoEvaluaAlertas() throws Exception {
        when(concursoRepository.findById(1L)).thenReturn(Optional.empty());

        service.modificar(1L, concurso(EstadoConcurso.PUBLICADO));

        verify(alertaService, never()).evaluarConcursoSinCerrar(any());
    }

    @Test
    void alEliminarElConcursoSeResuelvenSusAlertasEnElActo() throws Exception {
        Concurso concurso = concurso(EstadoConcurso.EN_PROCESO);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));

        assertTrue(service.bajaLogica(1L));

        assertTrue(concurso.getEliminado(), "La baja es lógica: el concurso se conserva.");
        verify(alertaService).evaluarConcursoSinCerrar(concurso);
    }
}
