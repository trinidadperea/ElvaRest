package com.elvarest.cliente.controllers;

import com.elvarest.cliente.config.SecurityConfig;
import com.elvarest.cliente.dto.AlertaDTO;
import com.elvarest.cliente.services.AlertaService;
import com.elvarest.cliente.services.CertificadoService;
import com.elvarest.cliente.services.ConcursoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Crear, editar, eliminar y marcar como leída una alerta es exclusivo de ADMIN: un usuario con
 * otro rol recibe 403 aunque escriba la URL a mano, y la acción no llega al Servidor.
 */
@WebMvcTest(controllers = AlertaController.class, properties = {
        "spring.security.user.name=test", "spring.security.user.password=test",
        "api.user=test", "api.password=test"})
@Import(SecurityConfig.class)
class AlertaSeguridadTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AlertaService alertaService;
    @MockitoBean
    private CertificadoService certificadoService;
    @MockitoBean
    private ConcursoService concursoService;

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioNoAdminNoPuedeAbrirLasPantallasDeAltaEdicionNiBaja() throws Exception {
        mvc.perform(get("/alertas/nuevo")).andExpect(status().isForbidden());
        mvc.perform(get("/alertas/alta")).andExpect(status().isForbidden());
        mvc.perform(get("/alertas/1/editar")).andExpect(status().isForbidden());
        mvc.perform(get("/alertas/modificar/1")).andExpect(status().isForbidden());
        mvc.perform(get("/alertas/baja/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioNoAdminNoPuedeGuardarNiMarcarComoLeida() throws Exception {
        mvc.perform(post("/alertas/actualizar").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/alertas/1/marcar-leida").with(csrf())).andExpect(status().isForbidden());

        verify(alertaService, never()).modificar(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void elAdminPuedeMarcarComoLeida() throws Exception {
        AlertaDTO alerta = new AlertaDTO();
        alerta.setId(1L);
        when(alertaService.obtener(1L)).thenReturn(alerta);

        mvc.perform(post("/alertas/1/marcar-leida").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/alertas/1"));

        verify(alertaService).modificar(eq(1L), any());
    }
}
