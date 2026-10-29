package com.elvarest.cliente;

import com.elvarest.cliente.dto.CertificadoDTO;
import com.elvarest.cliente.dto.ConcursoDTO;
import com.elvarest.cliente.dto.ConcursoDocenteDTO;
import com.elvarest.cliente.dto.DocenteDTO;
import com.elvarest.cliente.dto.TipoCertificadoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Render del fragmento de carga de certificados. Verifica que el tipo fijo (renovación) y el
 * paso de parámetros nulos (carga desde el concurso) no rompan la plantilla.
 */
class RenderCargaCertificadoTest {

    private SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private WebContext context(boolean renovacion) {
        MockServletContext servletContext = new MockServletContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"));
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(
                application.buildExchange(request, new MockHttpServletResponse()), request.getLocale());

        CertificadoDTO item = new CertificadoDTO();
        item.setTipoCertificado(tipo(3L));
        context.setVariable("postulacionId", 12L);
        context.setVariable("item", item);
        context.setVariable("tiposCertificado", List.of(tipo(3L)));
        context.setVariable("tipoFijo", renovacion ? tipo(3L) : null);
        context.setVariable("certificadoOrigenId", renovacion ? 55L : null);
        return context;
    }

    private TipoCertificadoDTO tipo(Long id) {
        TipoCertificadoDTO tipo = mock(TipoCertificadoDTO.class);
        when(tipo.getId()).thenReturn(id);
        when(tipo.getNombre()).thenReturn("Apto psicofísico");
        when(tipo.isPermiteProvisorio()).thenReturn(true);
        return tipo;
    }

    private CertificadoDTO certificado() {
        DocenteDTO docente = mock(DocenteDTO.class);
        when(docente.getId()).thenReturn(8L);
        when(docente.getApellido()).thenReturn("Pérez");
        when(docente.getNombre()).thenReturn("Ana");
        ConcursoDTO concurso = mock(ConcursoDTO.class);
        when(concurso.getId()).thenReturn(4L);
        when(concurso.getDescripcion()).thenReturn("Concurso de prueba");
        ConcursoDocenteDTO postulacion = mock(ConcursoDocenteDTO.class);
        when(postulacion.getId()).thenReturn(12L);
        when(postulacion.getDocente()).thenReturn(docente);
        when(postulacion.getConcurso()).thenReturn(concurso);

        CertificadoDTO certificado = new CertificadoDTO();
        certificado.setId(55L);
        certificado.setConcursoDocente(postulacion);
        certificado.setTipoCertificado(tipo(3L));
        certificado.setFechaCarga(LocalDate.of(2026, 1, 5));
        certificado.setActual(true);
        return certificado;
    }

    @Test
    void laRenovacionMuestraElTipoFijoYLoEnviaOculto() {
        String html = engine().process("prueba-carga-fija", context(true));

        assertTrue(html.contains("form-control-plaintext"));
        assertTrue(html.contains("Apto psicofísico"));
        assertTrue(html.contains("data-permite-provisorio=\"true\""));
        assertTrue(html.contains("name=\"tipoCertificado.id\" value=\"3\""));
        assertTrue(html.contains("name=\"certificadoOrigen\" value=\"55\""));
        assertFalse(html.contains("<select"));
    }

    @Test
    void laCargaNormalConservaElSelectDeTipos() {
        String html = engine().process("prueba-carga-select", context(false));

        assertTrue(html.contains("<select"));
        assertTrue(html.contains("name=\"tipoCertificado.id\""));
        assertFalse(html.contains("form-control-plaintext"));
    }

    @Test
    void elDetalleUsaElEncabezadoDeSeccionYRenuevaPorCertificado() {
        WebContext context = context(true);
        context.setVariable("item", certificado());

        String html = engine().process("certificados/detalle", Set.of("#contenido"), context);

        assertTrue(html.contains("card-title section-title mb-0\">Datos del certificado"));
        assertTrue(html.contains("/certificados/nuevo/postulacion/12?certificado=55"));
        assertFalse(html.contains("?tipo="));
    }

    @Test
    void laPantallaDeRenovacionUsaElEncabezadoDeSeccionYElTipoFijo() {
        WebContext context = context(true);
        CertificadoDTO item = certificado();
        context.setVariable("item", item);
        context.setVariable("postulacion", item.getConcursoDocente());
        context.setVariable("certificados", List.of());

        String html = engine().process("certificados/nuevo", Set.of("#contenido"), context);

        assertTrue(html.contains("card-title section-title mb-0\">Certificados de este concurso"));
        assertTrue(html.contains("card-title section-title mb-0\">Cargar / renovar certificado"));
        assertTrue(html.contains("form-control-plaintext"));
        assertFalse(html.contains("<select"));
    }
}
