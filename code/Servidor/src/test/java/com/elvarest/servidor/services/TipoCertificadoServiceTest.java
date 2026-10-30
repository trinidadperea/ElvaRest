package com.elvarest.servidor.services;

import com.elvarest.servidor.entities.Certificado;
import com.elvarest.servidor.entities.TipoCertificado;
import com.elvarest.servidor.exceptions.ErrorServiceException;
import com.elvarest.servidor.repositories.CertificadoRepository;
import com.elvarest.servidor.repositories.TipoCertificadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Regla de eliminación de tipos de certificado: no se elimina un tipo que ya tiene certificados
 * cargados en concursos. Los certificados lo referencian de forma obligatoria y la postulación se
 * validó con ese tipo, así que borrarlo dejaría esos registros sin control de obligatoriedad.
 */
@ExtendWith(MockitoExtension.class)
class TipoCertificadoServiceTest {

    private static final String MENSAJE_CERTIFICADOS =
            "No se puede eliminar el tipo de certificado porque hay certificados cargados de este tipo en concursos.";

    @Mock
    private TipoCertificadoRepository tipoCertificadoRepository;
    @Mock
    private CertificadoRepository certificadoRepository;

    private TipoCertificadoService service;
    private TipoCertificado tipo;

    @BeforeEach
    void setUp() {
        service = new TipoCertificadoService(tipoCertificadoRepository, certificadoRepository);
        tipo = new TipoCertificado();
        tipo.setId(1L);
        tipo.setCodigo("APTO");
        tipo.setNombre("Apto psicofísico");
    }

    private Certificado certificado(Long tipoId, boolean eliminado) {
        Certificado certificado = new Certificado();
        certificado.setId(40L);
        TipoCertificado delTipo = new TipoCertificado();
        delTipo.setId(tipoId);
        certificado.setTipoCertificado(delTipo);
        certificado.setEliminado(eliminado);
        return certificado;
    }

    @Test
    void tipoConCertificadosCargadosRechazaEliminacion() {
        when(tipoCertificadoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(certificadoRepository.findAll()).thenReturn(List.of(certificado(1L, false)));

        ErrorServiceException ex = assertThrows(ErrorServiceException.class, () -> service.validarEliminacion(1L));
        assertEquals(MENSAJE_CERTIFICADOS, ex.getMessage());
    }

    @Test
    void tipoDeOtroTipoDeCertificadoPermiteEliminacion() {
        when(tipoCertificadoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(certificadoRepository.findAll()).thenReturn(List.of(certificado(99L, false)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void certificadoEliminadoNoBloqueaEliminacion() {
        when(tipoCertificadoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(certificadoRepository.findAll()).thenReturn(List.of(certificado(1L, true)));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void tipoSinCertificadosPermiteEliminacion() {
        when(tipoCertificadoRepository.findById(1L)).thenReturn(Optional.of(tipo));
        when(certificadoRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void tipoInexistentePermiteEliminacion() {
        when(tipoCertificadoRepository.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }

    @Test
    void tipoYaEliminadoPermiteEliminacion() {
        tipo.setEliminado(true);
        when(tipoCertificadoRepository.findById(1L)).thenReturn(Optional.of(tipo));

        assertDoesNotThrow(() -> service.validarEliminacion(1L));
    }
}
