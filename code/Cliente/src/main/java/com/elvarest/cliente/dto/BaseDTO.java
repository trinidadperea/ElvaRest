package com.elvarest.cliente.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseDTO implements Identifiable<Long>{
    private Long id;
    private Boolean eliminado = false;

    @Override
    public Long getId() {
        return this.id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
