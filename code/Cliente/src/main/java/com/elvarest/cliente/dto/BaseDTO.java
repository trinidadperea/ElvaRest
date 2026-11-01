package com.elvarest.cliente.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseDTO implements Identifiable<String>{
    /** Identificador público (UUID): el id numérico de base ya no viaja en el JSON de la API. */
    private String uuid;
    private Boolean eliminado = false;

    /**
     * Alias de uuid solo para el Cliente (formularios y controladores). No debe viajar en el JSON hacia la API:
     * el Servidor espera un id numérico en ese campo y rechazaría el uuid con un 400.
     */
    @Override
    @JsonIgnore
    public String getId() {
        return this.uuid;
    }

    @Override
    public void setId(String id) {
        this.uuid = id;
    }
}