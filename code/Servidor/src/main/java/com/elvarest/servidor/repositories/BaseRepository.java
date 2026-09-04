package com.elvarest.servidor.repositories;

import com.elvarest.servidor.entities.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID extends Serializable> extends JpaRepository<T, ID> {

    List<T> findByActivoTrue();

    Optional<T> findByIdAndActivoTrue(ID id);

    List<T> findAllById(Iterable<ID> ids);
}
