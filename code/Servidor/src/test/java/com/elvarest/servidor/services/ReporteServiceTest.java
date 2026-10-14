package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.enums.EstadoConcurso;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.ReporteRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Reporte PDF de concurso: datos, resumen de horas (regla 3.10) y postulaciones, incluidas las canceladas.
 */
@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    @Mock
    private ReporteRepository reporteRepository;
    @Mock
    private ConcursoRepository concursoRepository;
    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;

    private ReporteService service;
    private Concurso concurso;

    @BeforeEach
    void setUp() {
        service = new ReporteService(reporteRepository, concursoRepository, concursoDocenteRepository);

        Cargo cargo = new Cargo();
        cargo.setDescripcion("Profesor de Matemática");
        cargo.setCargaHoraria(20);

        concurso = new Concurso();
        concurso.setId(1L);
        concurso.setDescripcion("Concurso Matemática 2026");
        concurso.setEstadoConcurso(EstadoConcurso.EN_PROCESO);
        concurso.setFechaApertura(LocalDate.of(2026, 10, 1));
        concurso.setFechaCierre(LocalDate.of(2026, 10, 10));
        concurso.setCargo(cargo);
        concurso.setHorasTotales(20);
        concurso.setHorasTransferidas(2);
    }

    private ConcursoDocente postulacion(String apellido, Integer horas, EstadoPostulacion estado,
                                        String observacion, boolean eliminada) {
        Docente docente = new Docente();
        docente.setNombre("Ana");
        docente.setApellido(apellido);
        docente.setNumeroDocumento("30111222");

        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setDocente(docente);
        postulacion.setConcurso(concurso);
        postulacion.setHorasElegidas(horas);
        postulacion.setEstadoPostulacion(estado);
        postulacion.setObservacion(observacion);
        postulacion.setEliminado(eliminada);
        return postulacion;
    }

    private String textoDelPdf(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina)).append('\n');
        }
        reader.close();
        // Las celdas angostas parten el texto en varias líneas: se normalizan los espacios.
        return texto.toString().replaceAll("\\s+", " ");
    }

    @Test
    void reporteIncluyeCanceladasYCalculaHorasSoloConVigentes() throws Exception {
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(1L)).thenReturn(List.of(
                postulacion("Gomez", 8, EstadoPostulacion.DESIGNACION_DEFINITIVA, null, false),
                postulacion("Perez", 6, EstadoPostulacion.CANCELADO_INEFECTIVO,
                        "No se presentó a la toma de posesión", false),
                postulacion("Borrada", 4, EstadoPostulacion.PENDIENTE_CERTIFICADOS, null, true)));

        String texto = textoDelPdf(service.generarReporteConcurso(1L));

        assertTrue(texto.contains("Concurso Matemática 2026"));
        assertTrue(texto.contains("Gomez, Ana"));
        assertTrue(texto.contains("Perez, Ana"));
        assertTrue(texto.contains("Cancelado / inefectivo"));
        assertTrue(texto.contains("No se presentó a la toma de posesión"));
        assertFalse(texto.contains("Borrada"), "Las postulaciones eliminadas no se listan");
        // Totales 20, asignadas 8 (la cancelada no suma), transferidas 2, disponibles 10.
        assertTrue(texto.contains("20 8 2 10"), texto);
    }

    @Test
    void concursoSinPostulacionesLoIndica() throws Exception {
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        when(concursoDocenteRepository.findByConcursoIdOrderByIdAsc(1L)).thenReturn(List.of());

        assertTrue(textoDelPdf(service.generarReporteConcurso(1L)).contains("No hay docentes postulados."));
    }

    @Test
    void concursoInexistenteOEliminadoLanzaError() {
        when(concursoRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ErrorServiceException.class, () -> service.generarReporteConcurso(99L));

        concurso.setEliminado(true);
        when(concursoRepository.findById(1L)).thenReturn(Optional.of(concurso));
        assertThrows(ErrorServiceException.class, () -> service.generarReporteConcurso(1L));
    }
}
