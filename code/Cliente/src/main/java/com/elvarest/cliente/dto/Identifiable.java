package com.elvarest.cliente.dto;

public interface Identifiable<ID> {
    ID getId();
    void setId(ID id);
}
