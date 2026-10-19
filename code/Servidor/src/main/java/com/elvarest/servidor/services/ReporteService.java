package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Cargo;
import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.Materia;
import com.elvarest.servidor.entities.Reporte;
import com.elvarest.servidor.entities.enums.EstadoPostulacion;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.ReporteRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReporteService extends BaseService<Reporte, Long> {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color GRIS_ENCABEZADO = new Color(230, 230, 230);

    private final ConcursoRepository concursoRepository;
    private final ConcursoDocenteRepository concursoDocenteRepository;

    public ReporteService(
            ReporteRepository reporteRepository,
            ConcursoRepository concursoRepository,
            ConcursoDocenteRepository concursoDocenteRepository) {

        super(reporteRepository);
        this.concursoRepository = concursoRepository;
        this.concursoDocenteRepository = concursoDocenteRepository;
    }

    /**
     * Genera el PDF de un concurso: datos generales, resumen de horas (§3.10 de las reglas de negocio)
     * y todas sus postulaciones —incluidas las canceladas— en orden de registro.
     */
    public byte[] generarReporteConcurso(Long concursoId) throws ErrorServiceException {

        Concurso concurso = concursoRepository.findById(concursoId)
                .filter(c -> !Boolean.TRUE.equals(c.getEliminado()))
                .orElseThrow(() -> new ErrorServiceException("Concurso no encontrado"));

        List<ConcursoDocente> postulaciones = concursoDocenteRepository
                .findByConcursoIdOrderByIdAsc(concursoId).stream()
                .filter(postulacion -> !Boolean.TRUE.equals(postulacion.getEliminado()))
                .toList();

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document documento = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(documento, outputStream);

            documento.open();
            agregarTitulo(documento);
            agregarDatosConcurso(documento, concurso);
            agregarResumenHoras(documento, concurso, postulaciones);
            agregarPostulaciones(documento, postulaciones);
            agregarFechaGeneracion(documento);
            documento.close();

            return outputStream.toByteArray();

        } catch (DocumentException e) {
            throw new ErrorServiceException("Error al generar el reporte PDF");
        }
    }

    private void agregarTitulo(Document documento) throws DocumentException {
        Paragraph titulo = new Paragraph("REPORTE DE CONCURSO",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(20);
        documento.add(titulo);
    }

    private void agregarDatosConcurso(Document documento, Concurso concurso) throws DocumentException {
        agregarSubtitulo(documento, "DATOS DEL CONCURSO");

        PdfPTable tabla = new PdfPTable(new float[]{1.3f, 3f});
        tabla.setWidthPercentage(100);

        agregarFila(tabla, "Concurso", concurso.getDescripcion());
        agregarFila(tabla, "Estado", texto(concurso.getEstadoConcurso()));

        Cargo cargo = concurso.getCargo();
        if (cargo != null) {
            agregarFila(tabla, "Cargo", cargo.getDescripcion());
            Materia materia = cargo.getMateria();
            if (materia != null) {
                agregarFila(tabla, "Materia", materia.getNombre());
                if (materia.getCurso() != null) {
                    agregarFila(tabla, "Curso", materia.getCurso().getAnio() + "° año");
                }
            }
        }

        agregarFila(tabla, "Carácter de designación", texto(concurso.getCaracterDesignacion()));
        agregarFila(tabla, "Origen de la vacancia", concurso.getOrigenVacancia());
        agregarFila(tabla, "Fecha de apertura", fecha(concurso.getFechaApertura()));
        // La fecha del concurso se persiste en fechaCierre (§2 de las reglas de negocio).
        agregarFila(tabla, "Fecha del concurso", fecha(concurso.getFechaCierre()));

        if (Boolean.TRUE.equals(concurso.getEsRellamado())) {
            String origen = concurso.getConcursoOrigenId() == null ? null
                    : concursoRepository.findById(concurso.getConcursoOrigenId())
                    .map(Concurso::getDescripcion)
                    .orElse(null);
            agregarFila(tabla, "Rellamado de", origen);
        }

        documento.add(tabla);
    }

    private void agregarResumenHoras(Document documento, Concurso concurso,
                                     List<ConcursoDocente> postulaciones) throws DocumentException {
        agregarSubtitulo(documento, "RESUMEN DE HORAS");

        Integer totales = ReglasConcurso.horasTotales(concurso);
        int asignadas = ReglasConcurso.horasAsignadas(postulaciones);
        int transferidas = concurso.getHorasTransferidas() != null ? concurso.getHorasTransferidas() : 0;
        Integer disponibles = ReglasConcurso.horasDisponibles(concurso, postulaciones);

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        agregarCeldaEncabezado(tabla, "Totales");
        agregarCeldaEncabezado(tabla, "Asignadas");
        agregarCeldaEncabezado(tabla, "Transferidas a rellamado");
        agregarCeldaEncabezado(tabla, "Disponibles");

        agregarCelda(tabla, totales == null ? "-" : String.valueOf(totales));
        agregarCelda(tabla, String.valueOf(asignadas));
        agregarCelda(tabla, String.valueOf(transferidas));
        agregarCelda(tabla, disponibles == null ? "-" : String.valueOf(disponibles));

        documento.add(tabla);
    }

    private void agregarPostulaciones(Document documento, List<ConcursoDocente> postulaciones)
            throws DocumentException {
        agregarSubtitulo(documento, "POSTULACIONES");

        if (postulaciones.isEmpty()) {
            documento.add(new Paragraph("No hay docentes postulados."));
            return;
        }

        PdfPTable tabla = new PdfPTable(new float[]{0.5f, 2.1f, 1.4f, 0.8f, 2f, 1.4f, 2.4f});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);

        agregarCeldaEncabezado(tabla, "N°");
        agregarCeldaEncabezado(tabla, "Docente");
        agregarCeldaEncabezado(tabla, "Documento");
        agregarCeldaEncabezado(tabla, "Horas");
        agregarCeldaEncabezado(tabla, "Estado");
        agregarCeldaEncabezado(tabla, "Designado");
        agregarCeldaEncabezado(tabla, "Observación");

        int numero = 1;
        for (ConcursoDocente postulacion : postulaciones) {
            Docente docente = postulacion.getDocente();
            EstadoPostulacion estado = ReglasConcurso.estadoEfectivo(postulacion);

            agregarCelda(tabla, String.valueOf(numero++));
            agregarCelda(tabla, docente == null ? "-" : docente.getApellido() + ", " + docente.getNombre());
            agregarCelda(tabla, docente == null ? "-" : docente.getNumeroDocumento());
            agregarCelda(tabla, postulacion.getHorasElegidas() == null ? "-"
                    : String.valueOf(postulacion.getHorasElegidas()));
            agregarCelda(tabla, ReglasConcurso.etiqueta(estado));
            agregarCelda(tabla, estado == EstadoPostulacion.DESIGNACION_DEFINITIVA ? "Sí" : "No");
            agregarCelda(tabla, postulacion.getObservacion());
        }

        documento.add(tabla);
    }

    private void agregarFechaGeneracion(Document documento) throws DocumentException {
        Paragraph fecha = new Paragraph("Fecha de generación: " + LocalDate.now().format(FORMATO_FECHA),
                FontFactory.getFont(FontFactory.HELVETICA, 9));
        fecha.setSpacingBefore(20);
        fecha.setAlignment(Element.ALIGN_RIGHT);
        documento.add(fecha);
    }

    // --- Utilidades de formato ---

    private void agregarSubtitulo(Document documento, String texto) throws DocumentException {
        Paragraph subtitulo = new Paragraph(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13));
        subtitulo.setSpacingBefore(14);
        subtitulo.setSpacingAfter(8);
        documento.add(subtitulo);
    }

    private void agregarFila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Phrase(etiqueta, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        celdaEtiqueta.setBackgroundColor(GRIS_ENCABEZADO);
        celdaEtiqueta.setPadding(5);
        tabla.addCell(celdaEtiqueta);
        agregarCelda(tabla, valor);
    }

    private void agregarCeldaEncabezado(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        celda.setBackgroundColor(GRIS_ENCABEZADO);
        celda.setPadding(5);
        tabla.addCell(celda);
    }

    private void agregarCelda(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(valorOGuion(texto), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        celda.setPadding(5);
        tabla.addCell(celda);
    }

    private String valorOGuion(String texto) {
        return texto == null || texto.isBlank() ? "-" : texto;
    }

    private String fecha(LocalDate fecha) {
        return fecha == null ? null : fecha.format(FORMATO_FECHA);
    }

    private String texto(Enum<?> valor) {
        return valor == null ? null : valor.name().replace('_', ' ');
    }
}
