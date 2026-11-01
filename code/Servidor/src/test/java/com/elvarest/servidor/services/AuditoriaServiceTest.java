package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Auditoria;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.AuditoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    private AuditoriaService service;

    @BeforeEach
    void setUp() {
        service = new AuditoriaService(auditoriaRepository);
    }

    @Test
    void listarActivosDevuelveElHistorialMasRecientePrimero() throws ErrorServiceException {
        List<Auditoria> historial = List.of(new Auditoria(), new Auditoria());
        when(auditoriaRepository.findAllByOrderByFechaHoraDesc()).thenReturn(historial);

        assertEquals(historial, service.listarActivos());
    }

    @Test
    void registrarAsignaUuidYEliminadoYGuarda() {
        when(auditoriaRepository.save(any(Auditoria.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        Auditoria guardada = service.registrar(new Auditoria());

        // El uuid lo asigna el servidor y el registro nunca nace eliminado.
        assertFalse(Boolean.TRUE.equals(guardada.getEliminado()));
        assertNotNull(guardada.getUuid());
        assertEquals(36, guardada.getUuid().length());
        verify(auditoriaRepository).save(any(Auditoria.class));
    }
}
