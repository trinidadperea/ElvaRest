package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Concurso;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.Docente;
import com.elvarest.servidor.entities.Reporte;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.ConcursoRepository;
import com.elvarest.servidor.repositories.ReporteRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReporteService extends BaseService<Reporte, Long> {

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

    public byte[] generarReporteConcurso(Long concursoId) {

        Concurso concurso = concursoRepository.findById(concursoId)
                .orElseThrow(() ->
                        new RuntimeException("Concurso no encontrado"));

        List<ConcursoDocente> ordenMerito =
                concursoDocenteRepository
                        .findByConcursoIdOrderByDocentePuntajeDesc(concursoId);

        try {
            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            Document documento = new Document();

            PdfWriter.getInstance(documento, outputStream);

            documento.open();

            agregarTitulo(documento);
            agregarDatosConcurso(documento, concurso);
            agregarOrdenMerito(documento, ordenMerito);
            agregarFechaGeneracion(documento);

            documento.close();

            return outputStream.toByteArray();

        } catch (DocumentException e) {
            throw new RuntimeException(
                    "Error al generar el reporte PDF", e);
        }
    }

    private void agregarTitulo(Document documento)
            throws DocumentException {

        Font fuenteTitulo =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        18
                );

        Paragraph titulo =
                new Paragraph(
                        "REPORTE DE CONCURSO",
                        fuenteTitulo
                );

        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(20);

        documento.add(titulo);
    }

    private void agregarDatosConcurso(
            Document documento,
            Concurso concurso)
            throws DocumentException {

        DateTimeFormatter formato =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");

        documento.add(
                new Paragraph(
                        "Concurso: " + concurso.getDescripcion()
                )
        );

        documento.add(
                new Paragraph(
                        "Estado: " + concurso.getEstadoConcurso()
                )
        );

        documento.add(
                new Paragraph(
                        "Fecha de apertura: "
                                + concurso.getFechaApertura()
                                .format(formato)
                )
        );

        documento.add(
                new Paragraph(
                        "Fecha de cierre: "
                                + concurso.getFechaCierre()
                                .format(formato)
                )
        );

        if (concurso.getCargo() != null) {
            documento.add(
                    new Paragraph(
                            "Cargo: "
                                    + concurso.getCargo()
                                    .getDescripcion()
                    )
            );
        }

        documento.add(new Paragraph(" "));
    }

    private void agregarOrdenMerito(
            Document documento,
            List<ConcursoDocente> ordenMerito)
            throws DocumentException {

        Font subtitulo =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        14
                );

        Paragraph titulo =
                new Paragraph(
                        "ORDEN DE MERITO",
                        subtitulo
                );

        titulo.setSpacingAfter(10);

        documento.add(titulo);

        if (ordenMerito.isEmpty()) {
            documento.add(
                    new Paragraph(
                            "No hay docentes postulados."
                    )
            );

            return;
        }

        PdfPTable tabla = new PdfPTable(5);

        tabla.setWidthPercentage(100);

        agregarCeldaEncabezado(tabla, "Orden");
        agregarCeldaEncabezado(tabla, "Docente");
        agregarCeldaEncabezado(tabla, "Puntaje");
        agregarCeldaEncabezado(tabla, "Area");
        agregarCeldaEncabezado(tabla, "Interno");

        int posicion = 1;

        for (ConcursoDocente concursoDocente : ordenMerito) {

            Docente docente =
                    concursoDocente.getDocente();

            tabla.addCell(
                    String.valueOf(posicion)
            );

            tabla.addCell(
                    docente.getNombre()
                            + " "
                            + docente.getApellido()
            );

            tabla.addCell(
                    String.valueOf(docente.getPuntaje())
            );

            tabla.addCell(
                    docente.getArea()
            );

            tabla.addCell(
                    docente.isInterno()
                            ? "Si"
                            : "No"
            );

            posicion++;
        }

        documento.add(tabla);
    }

    private void agregarCeldaEncabezado(
            PdfPTable tabla,
            String texto) {

        Font fuente =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        10
                );

        PdfPCell celda =
                new PdfPCell(
                        new Phrase(texto, fuente)
                );

        tabla.addCell(celda);
    }

    private void agregarFechaGeneracion(
            Document documento)
            throws DocumentException {

        DateTimeFormatter formato =
                DateTimeFormatter.ofPattern("dd/MM/yyyy");

        Paragraph fecha =
                new Paragraph(
                        "\nFecha de generacion: "
                                + LocalDate.now()
                                .format(formato)
                );

        documento.add(fecha);
    }
}