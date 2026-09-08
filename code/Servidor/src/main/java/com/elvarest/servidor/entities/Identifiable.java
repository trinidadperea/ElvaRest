package com.elvarest.servidor.entities;

public interface Identifiable<ID> {
    ID getId();
    void setId(ID id);
}