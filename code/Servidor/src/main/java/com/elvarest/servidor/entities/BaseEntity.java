package com.elvarest.servidor.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.io.Serializable;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity<ID> implements Identifiable<ID> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Se acepta al deserializar (referencias de formularios existentes), pero no viaja en las
    // respuestas: evita la enumeración de registros por id numérico secuencial (IDOR).
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    protected ID id;
    protected Boolean eliminado = false;

    /** Identificador público de las peticiones REST: opaco, no permite enumerar registros. */
    @Column(unique = true, length = 36)
    protected String uuid;

    /** El uuid lo genera el servidor al persistir; cubre también los guardados por cascada. */
    @PrePersist
    protected void asignarUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

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

    public String getUuid() {
        return this.uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /*public  ID getId();
    public abstract void setId(ID id);
    public abstract Boolean getEliminado();
    public abstract void setEliminado(Boolean eliminado);*/
}