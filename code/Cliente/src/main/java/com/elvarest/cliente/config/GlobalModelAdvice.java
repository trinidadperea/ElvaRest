package com.elvarest.cliente.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Agrega atributos globales al modelo de todas las vistas.
 * Actualmente expone la ruta actual para resaltar la sección activa del menú.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
