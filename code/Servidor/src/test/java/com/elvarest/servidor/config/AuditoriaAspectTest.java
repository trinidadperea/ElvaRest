package com.elvarest.servidor.config;

import com.elvarest.servidor.entities.Alerta;
import com.elvarest.servidor.entities.Auditoria;
import com.elvarest.servidor.entities.enums.AccionAuditoria;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.services.AuditoriaService;
import com.elvarest.servidor.services.BaseService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El aspecto audita alta, modificar y bajaLogica de BaseService: registra el evento solo
 * cuando la operación tuvo éxito, con el login autenticado (o "SISTEMA") y el detalle JSON.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaAspectTest {

    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private Signature signature;
    @Mock
    private BaseService<Alerta, Long> targetService;

    private AuditoriaAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditoriaAspect(auditoriaService);
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void altaExitosaRegistraEventoConUsuarioAutenticado() throws Throwable {
        when(signature.getName()).thenReturn("alta");
        Alerta alerta = new Alerta();
        alerta.setUuid("uuid-alerta");
        alerta.setMensaje("Revisar horas");
        when(joinPoint.proceed()).thenReturn(alerta);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));

        Object resultado = aspect.auditar(joinPoint);

        assertEquals(alerta, resultado);
        Auditoria evento = capturarEvento();
        assertEquals(AccionAuditoria.ALTA, evento.getAccion());
        assertEquals("Alerta", evento.getEntidad());
        assertEquals("uuid-alerta", evento.getRegistroUuid());
        assertEquals("admin", evento.getUsuario());
        assertNotNull(evento.getFechaHora());
        assertTrue(evento.getDetalle().contains("Revisar horas"));
    }

    @Test
    void modificacionExitosaRegistraEventoConDiffDeCampos() throws Throwable {
        when(signature.getName()).thenReturn("modificar");
        Alerta previa = new Alerta();
        previa.setUuid("uuid-alerta");
        previa.setMensaje("Revisar horas");
        Alerta nueva = new Alerta();
        nueva.setUuid("uuid-alerta");
        nueva.setMensaje("Horas cubiertas");
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L, nueva});
        when(joinPoint.getTarget()).thenReturn(targetService);
        when(targetService.getOne(1L)).thenReturn(previa);
        when(joinPoint.proceed()).thenReturn(Optional.of(nueva));

        Object resultado = aspect.auditar(joinPoint);

        assertEquals(Optional.of(nueva), resultado);
        Auditoria evento = capturarEvento();
        assertEquals(AccionAuditoria.MODIFICACION, evento.getAccion());
        assertEquals("Alerta", evento.getEntidad());
        assertEquals("uuid-alerta", evento.getRegistroUuid());
        assertTrue(evento.getDetalle().contains("Revisar horas"));
        assertTrue(evento.getDetalle().contains("Horas cubiertas"));
    }

    @Test
    void bajaLogicaExitosaRegistraBajaSinDetalleYConUsuarioSistema() throws Throwable {
        when(signature.getName()).thenReturn("bajaLogica");
        Alerta previa = new Alerta();
        previa.setUuid("uuid-alerta");
        previa.setMensaje("Revisar horas");
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
        when(joinPoint.getTarget()).thenReturn(targetService);
        when(targetService.getOne(1L)).thenReturn(previa);
        when(joinPoint.proceed()).thenReturn(true);
        // Sin sesión autenticada: el evento queda a nombre de SISTEMA.

        Object resultado = aspect.auditar(joinPoint);

        assertEquals(true, resultado);
        Auditoria evento = capturarEvento();
        assertEquals(AccionAuditoria.BAJA, evento.getAccion());
        assertEquals("uuid-alerta", evento.getRegistroUuid());
        assertEquals("SISTEMA", evento.getUsuario());
        assertNull(evento.getDetalle());
    }

    @Test
    void bajaSinEfectoNoRegistraEvento() throws Throwable {
        when(signature.getName()).thenReturn("bajaLogica");
        Alerta previa = new Alerta();
        previa.setUuid("uuid-alerta");
        when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
        when(joinPoint.getTarget()).thenReturn(targetService);
        when(targetService.getOne(1L)).thenReturn(previa);
        when(joinPoint.proceed()).thenReturn(false);

        Object resultado = aspect.auditar(joinPoint);

        assertEquals(false, resultado);
        verify(auditoriaService, never()).registrar(any());
    }

    @Test
    void operacionRechazadaPorReglaDeNegocioNoRegistraEvento() throws Throwable {
        when(signature.getName()).thenReturn("alta");
        when(joinPoint.proceed()).thenThrow(new ErrorServiceException("Regla rechazada"));

        assertThrows(ErrorServiceException.class, () -> aspect.auditar(joinPoint));
        verify(auditoriaService, never()).registrar(any());
    }

    @Test
    void falloAlRegistrarLaAuditoriaNoRompeLaOperacion() throws Throwable {
        when(signature.getName()).thenReturn("alta");
        Alerta alerta = new Alerta();
        alerta.setUuid("uuid-alerta");
        when(joinPoint.proceed()).thenReturn(alerta);
        doThrow(new RuntimeException("Base de datos caída")).when(auditoriaService).registrar(any());

        // Fail-open: la operación ya aplicada sigue devolviendo su resultado.
        Object resultado = aspect.auditar(joinPoint);

        assertEquals(alerta, resultado);
    }

    private Auditoria capturarEvento() {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaService).registrar(captor.capture());
        return captor.getValue();
    }
}
