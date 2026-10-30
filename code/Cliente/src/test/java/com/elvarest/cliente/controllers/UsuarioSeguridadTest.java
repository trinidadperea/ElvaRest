package com.elvarest.cliente.controllers;

import com.elvarest.cliente.auth.AuthService;
import com.elvarest.cliente.auth.LoginSuccessHandler;
import com.elvarest.cliente.config.SecurityConfig;
import com.elvarest.cliente.config.WebConfig;
import com.elvarest.cliente.services.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La gestión de usuarios es exclusiva del ADMIN y sus URLs viajan con UUID públicos: un usuario
 * con otro rol recibe 403, los enlaces numéricos heredados del CRUD genérico quedan bloqueados y
 * el cambio de contraseña propia se resuelve con las credenciales guardadas en la sesión. Además,
 * mientras el flag de cambio obligatorio esté activo, el interceptor no deja pasar al resto del
 * sistema (solo la pantalla de cambio y el cierre de sesión).
 */
@WebMvcTest(controllers = UsuarioController.class, properties = {
        "spring.security.user.name=test", "spring.security.user.password=test",
        "api.user=test", "api.password=test"})
@Import({SecurityConfig.class, WebConfig.class})
class UsuarioSeguridadTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioController usuarioController;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UsuarioService usuarioService;

    @Test
    @WithMockUser(roles = "USER")
    void unUsuarioNoAdminNoPuedeAbrirLaGestionDeUsuarios() throws Exception {
        mvc.perform(get("/usuarios/list")).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/nuevo")).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/ver/uuid-1")).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/editar/uuid-1")).andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/baja/uuid-1").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/uuid-1/restablecer-contrasena").with(csrf()))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).restablecerContraseña(any());
    }

    @Test
    void laPantallaDeCambioDeContrasenaExigeEstarAutenticado() throws Exception {
        mvc.perform(get("/usuarios/cambiar-contrasena"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void elCambioDeContrasenaPendienteBloqueaElRestoDelSistema() throws Exception {
        mvc.perform(get("/usuarios/list").sessionAttr(LoginSuccessHandler.ATRIBUTO_FLAG, true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/cambiar-contrasena"));

        mvc.perform(get("/usuarios/nuevo").sessionAttr(LoginSuccessHandler.ATRIBUTO_FLAG, true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/cambiar-contrasena"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void conElFlagActivoSoloSePuedeCerrarSesion() throws Exception {
        mvc.perform(post("/logout").with(csrf()).sessionAttr(LoginSuccessHandler.ATRIBUTO_FLAG, true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void elAdminRestableceLaContrasenaDeUnUsuarioPorSuUuid() throws Exception {
        mvc.perform(post("/usuarios/uuid-1/restablecer-contrasena")
                        .with(csrf())
                        .sessionAttr("usuariosUuidMap", Map.of("uuid-1", 1L)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/ver/uuid-1"));

        verify(usuarioService).restablecerContraseña(1L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void cualquierUsuarioAutenticadoPuedeCambiarSuPropiaContrasena() throws Exception {
        mvc.perform(post("/usuarios/cambiar-contrasena")
                        .with(csrf())
                        .param("contraseñaActual", "vieja")
                        .param("contraseñaNueva", "nueva")
                        .param("contraseñaConfirmacion", "nueva")
                        .sessionAttr(LoginSuccessHandler.ATRIBUTO_FLAG, true)
                        .sessionAttr(LoginSuccessHandler.ATRIBUTO_USUARIO_API, "jperez")
                        .sessionAttr(LoginSuccessHandler.ATRIBUTO_PASSWORD_API, "vieja"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(usuarioService).cambiarContraseña("jperez", "vieja", "vieja", "nueva");
    }

    @Test
    void lasContrasenasQueNoCoincidenNoLleganAlServidor() {
        String vista = usuarioController.cambiarContrasena("vieja", "nueva", "otra",
                new MockHttpSession(), new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("registration/cambiar-contrasena", vista);
        verify(usuarioService, never()).cambiarContraseña(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unUuidQueNoEstaEnLaSesionRedirigeAlListado() throws Exception {
        mvc.perform(get("/usuarios/ver/uuid-otro")
                        .sessionAttr("usuariosUuidMap", Map.of("uuid-1", 1L)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/list"))
                .andExpect(flash().attribute("msgError", "Enlace no válido o expirado."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void lasRutasNumericasHeredadasQuedanBloqueadas() throws Exception {
        mvc.perform(get("/usuarios/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/list"))
                .andExpect(flash().attribute("msgError", "Enlace no válido o expirado."));
        mvc.perform(get("/usuarios/consultar/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/list"));
        mvc.perform(get("/usuarios/modificar/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/list"));
        mvc.perform(get("/usuarios/baja/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/list"));
    }
}
