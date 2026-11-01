package com.elvarest.servidor.config;

import com.elvarest.servidor.entities.BaseEntity;
import com.elvarest.servidor.repositories.BaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Completa el uuid público de los registros anteriores a la columna (ddl-auto=update la crea
 * nullable). Se ejecuta una vez por arranque y es idempotente; los registros nuevos ya reciben
 * su uuid al persistir (ver {@link BaseEntity} y BaseService.alta).
 */
@Component
public class UuidBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UuidBackfillRunner.class);

    private final List<BaseRepository<?, ?>> repositories;

    public UuidBackfillRunner(List<BaseRepository<?, ?>> repositories) {
        this.repositories = repositories;
    }

    @Override
    public void run(ApplicationArguments args) {
        int completados = 0;
        for (BaseRepository<?, ?> repository : repositories) {
            for (Object entidad : repository.findAll()) {
                BaseEntity<?> base = (BaseEntity<?>) entidad;
                if (base.getUuid() == null) {
                    base.setUuid(UUID.randomUUID().toString());
                    guardar(repository, entidad);
                    completados++;
                }
            }
        }
        log.info("Backfill de UUID completo: {} registros actualizados.", completados);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void guardar(BaseRepository<?, ?> repository, Object entidad) {
        ((BaseRepository) repository).save(entidad);
    }
}
