package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.ConcursoDocente;
import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.ConcursoDocenteRepository;
import com.elvarest.servidor.repositories.TipoCertificadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renovación de certificados: el anterior del mismo tipo pasa al historial y su alerta de
 * vencimiento se resuelve en el momento, sin esperar la depuración diaria (§10).
 */
@ExtendWith(MockitoExtension.class)
class CertificadoServiceTest {

    @Mock
    private CertificadoRepository certificadoRepository;

    @Mock
    private ConcursoDocenteRepository concursoDocenteRepository;

    @Mock
    private TipoCertificadoRepository tipoCertificadoRepository;

    @Mock
    private AlertaService alertaService;

    private CertificadoService service;

    @BeforeEach
    void setUp() {
        service = new CertificadoService(certificadoRepository, concursoDocenteRepository,
                tipoCertificadoRepository, alertaService);
    }

    private Certificado certificado(Long id, ConcursoDocente postulacion, TipoCertificado tipo) {
        Certificado certificado = new Certificado();
        certificado.setId(id);
        certificado.setConcursoDocente(postulacion);
        certificado.setTipoCertificado(tipo);
        certificado.setActual(true);
        return certificado;
    }

    @Test
    void alRenovarResuelveLaAlertaDelCertificadoReemplazado() throws ErrorServiceException {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setId(10L);
        TipoCertificado tipo = new TipoCertificado();
        tipo.setId(2L);
        Certificado anterior = certificado(1L, postulacion, tipo);
        Certificado nuevo = certificado(2L, postulacion, tipo);

        when(certificadoRepository.findByConcursoDocenteIdAndTipoCertificadoIdAndActualTrueAndEliminadoFalse(10L, 2L))
                .thenReturn(List.of(anterior, nuevo));

        service.postAlta(nuevo);

        assertFalse(anterior.isActual(), "El certificado anterior debe pasar al historial.");
        verify(certificadoRepository).save(anterior);
        // El reemplazado se evalúa (su alerta queda resuelta) y el nuevo también (por si vence pronto).
        verify(alertaService).evaluarVencimiento(anterior);
        verify(alertaService).evaluarVencimiento(nuevo);
    }

    @Test
    void alDarDeBajaElCertificadoResuelveSuAlertaEnElActo() throws ErrorServiceException {
        ConcursoDocente postulacion = new ConcursoDocente();
        postulacion.setId(10L);
        TipoCertificado tipo = new TipoCertificado();
        tipo.setId(2L);
        Certificado certificado = certificado(3L, postulacion, tipo);

        when(certificadoRepository.findById(3L)).thenReturn(Optional.of(certificado));

        assertTrue(service.bajaLogica(3L));

        assertTrue(certificado.getEliminado(), "La baja es lógica: el certificado se conserva.");
        verify(alertaService).evaluarVencimiento(certificado);
    }
}
