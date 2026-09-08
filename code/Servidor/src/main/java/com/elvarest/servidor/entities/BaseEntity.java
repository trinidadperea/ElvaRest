package com.elvarest.servidor.entities;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.io.Serializable;

@MappedSuperclass
public abstract class BaseEntity<ID> implements Identifiable<ID> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected ID id;
    protected Boolean eliminado = false;

    @Override
    public ID getId() {
        return this.id;
    }

    @Override
    public void setId(ID id) {
        this.id = id;
    }

    public Boolean getEliminado() {
        return this.eliminado;
    }

    public void setEliminado(Boolean eliminado) {
        this.eliminado = eliminado;
    }

    /*public  ID getId();
    public abstract void setId(ID id);
    public abstract Boolean getEliminado();
    public abstract void setEliminado(Boolean eliminado);*/
}