package com.elvarest.cliente.dto;

import com.elvarest.cliente.dto.enums.PersonaDTO;
import com.elvarest.cliente.dto.enums.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsuarioDTO extends PersonaDTO {
    private String nombreUsuario;
    private String contraseña;
    private Rol rol;
}
