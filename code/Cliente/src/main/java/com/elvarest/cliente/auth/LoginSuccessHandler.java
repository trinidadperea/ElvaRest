package com.elvarest.cliente.auth;

import com.elvarest.cliente.dto.UsuarioDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Tras el login, guarda en la sesión las credenciales del usuario (las necesita el cambio de
 * contraseña propia, que el Servidor resuelve por el Principal del BasicAuth) y su flag de
 * cambio obligatorio, y redirige a la pantalla que corresponda.
 */
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    public static final String ATRIBUTO_USUARIO_API = "usuarioApi";
    public static final String ATRIBUTO_PASSWORD_API = "passwordApi";
    public static final String ATRIBUTO_FLAG = "debeCambiarPassword";

    private final AuthService authService;

    public LoginSuccessHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        String password = request.getParameter("password");

        HttpSession session = request.getSession();
        session.setAttribute(ATRIBUTO_USUARIO_API, username);
        session.setAttribute(ATRIBUTO_PASSWORD_API, password);

        UsuarioDTO usuario = authService.buscarUsuarioPorNombre(username);
        boolean debeCambiar = usuario != null && Boolean.TRUE.equals(usuario.getDebeCambiarPassword());
        session.setAttribute(ATRIBUTO_FLAG, debeCambiar);

        response.sendRedirect(debeCambiar ? "/usuarios/cambiar-contrasena" : "/");
    }
}
