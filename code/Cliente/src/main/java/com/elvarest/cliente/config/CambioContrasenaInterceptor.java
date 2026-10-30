package com.elvarest.cliente.config;

import com.elvarest.cliente.auth.LoginSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Mientras la sesión tenga pendiente el cambio de contraseña (debeCambiarPassword activo),
 * solo se permite la pantalla de cambio, el cierre de sesión, el login y los recursos
 * estáticos: el resto del sistema redirige a la pantalla obligatoria.
 */
public class CambioContrasenaInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute(LoginSuccessHandler.ATRIBUTO_FLAG))) {
            return true;
        }

        String uri = request.getRequestURI();
        if (esRutaPermitida(uri)) {
            return true;
        }

        response.sendRedirect(request.getContextPath() + "/usuarios/cambiar-contrasena");
        return false;
    }

    private boolean esRutaPermitida(String uri) {
        return "/usuarios/cambiar-contrasena".equals(uri)
                || "/logout".equals(uri)
                || "/login".equals(uri)
                || "/error".equals(uri)
                || uri.startsWith("/adminlte/")
                || uri.startsWith("/css/")
                || uri.startsWith("/js/");
    }
}
