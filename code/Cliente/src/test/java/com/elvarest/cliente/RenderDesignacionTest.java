package com.elvarest.cliente;

import com.elvarest.cliente.dto.DesignacionDTO;
import com.elvarest.cliente.dto.enums.CaracterDesignacion;
import com.elvarest.cliente.dto.enums.EstadoDesignacion;
import com.elvarest.cliente.dto.enums.MotivoVacancia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.validation.BindingResult;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.support.RequestContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.context.webmvc.SpringWebMvcThymeleafRequestContext;
import org.thymeleaf.spring6.naming.SpringContextVariableNames;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los formularios de designación ya no eligen el estado: se muestra como dato calculado (lo deriva
 * el Servidor de la fecha de fin) y, solo si la designación está pendiente, se ofrece confirmar la
 * toma de posesión.
 */
class RenderDesignacionTest {

    private MockServletContext servletContext;
    private GenericWebApplicationContext applicationContext;

    @BeforeEach
    void prepararContextoDeAplicacion() {
        servletContext = new MockServletContext();
        applicationContext = new GenericWebApplicationContext();
        applicationContext.setServletContext(servletContext);
        applicationContext.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
    }

    @AfterEach
    void cerrarContextoDeAplicacion() {
        applicationContext.close();
    }

    private SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private WebContext context(EstadoDesignacion estado) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"));
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(
                application.buildExchange(request, new MockHttpServletResponse()), request.getLocale());

        DesignacionDTO item = new DesignacionDTO();
        item.setId(15L);
        item.setEstadoDesignacion(estado);
        item.setFechaInicioEfectiva(LocalDate.of(2026, 3, 1));
        item.setMotivoVacancia(MotivoVacancia.LICENCIA);
        item.setCaracterDesignacion(CaracterDesignacion.INTERINO);

        // Los formularios usan th:field: hay que exponer el RequestContext de Spring MVC y el
        // BindingResult del objeto del formulario, como lo hace ThymeleafView en la aplicación.
        request.setAttribute(BindingResult.MODEL_KEY_PREFIX + "item", new BeanPropertyBindingResult(item, "item"));
        context.setVariable(SpringContextVariableNames.THYMELEAF_REQUEST_CONTEXT,
                new SpringWebMvcThymeleafRequestContext(
                        new RequestContext(request, servletContext), request));

        context.setVariable("item", item);
        context.setVariable("docentes", List.of());
        return context;
    }

    @Test
    void laEdicionMuestraElEstadoCalculadoYSinSelector() {
        String html = engine().process("designaciones/editar", Set.of("#contenido"), context(EstadoDesignacion.ACTIVO));

        assertFalse(html.contains("name=\"estadoDesignacion\""), "El estado ya no se elige en el formulario.");
        assertTrue(html.contains("Se actualiza solo al guardar"));
        assertFalse(html.contains("confirmarPosesion"), "Solo se ofrece para las designaciones pendientes.");
    }

    @Test
    void laEdicionDeUnaDesignacionPendienteOfreceConfirmarLaPosesion() {
        String html = engine().process("designaciones/editar", Set.of("#contenido"), context(EstadoDesignacion.PENDIENTE));

        assertTrue(html.contains("name=\"confirmarPosesion\""));
        assertTrue(html.contains("Confirmar toma de posesión"));
        assertFalse(html.contains("Se actualiza solo al guardar"));
    }

    @Test
    void elAltaManualTampocoOfreceSelectorDeEstado() {
        String html = engine().process("designaciones/nuevo", Set.of("#contenido"), context(null));

        assertFalse(html.contains("name=\"estadoDesignacion\""), "El estado se calcula al guardar.");
        assertTrue(html.contains("Se calcula al guardar"));
    }
}
