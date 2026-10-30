-- =====================================================================
-- Migraciones manuales de la base gestion_concursos (MySQL 8)
-- =====================================================================
-- spring.jpa.hibernate.ddl-auto=update agrega tablas y columnas nuevas,
-- pero NO elimina columnas, NO quita NOT NULL ni índices UNIQUE y NO
-- actualiza los valores de un ENUM. Estos cambios se aplican acá.
--
-- El script es idempotente: cada paso verifica el estado actual y solo
-- se ejecuta si hace falta, así que puede correrse más de una vez.
--
-- Uso (con el Servidor detenido):
--   mysql -u USUARIO -p gestion_concursos < code/docs/migraciones.sql
--
-- Al agregar una migración nueva, seguir el mismo patrón y anotar la
-- regla de negocio que la origina.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. docente.puntaje: atributo retirado del modelo (§8 reglas de negocio).
--    Era NOT NULL sin valor por defecto: bloquea el alta de docentes.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'docente' AND COLUMN_NAME = 'puntaje') > 0,
    'ALTER TABLE docente DROP COLUMN puntaje',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 2. curso.seccion: atributo retirado del modelo (§8).
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'curso' AND COLUMN_NAME = 'seccion') > 0,
    'ALTER TABLE curso DROP COLUMN seccion',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 3. designacion.fecha_fin_estimada: atributo retirado del modelo (§8).
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion' AND COLUMN_NAME = 'fecha_fin_estimada') > 0,
    'ALTER TABLE designacion DROP COLUMN fecha_fin_estimada',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 4. designacion.fecha_fin_definitiva: pasó a ser opcional (queda vacía
--    mientras la designación sigue vigente).
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion'
       AND COLUMN_NAME = 'fecha_fin_definitiva' AND IS_NULLABLE = 'NO') > 0,
    'ALTER TABLE designacion MODIFY fecha_fin_definitiva DATE NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 5. designacion.docente_id: la relación pasó de @OneToOne a @ManyToOne.
--    Se permiten designaciones históricas del mismo docente (§4), así que
--    el UNIQUE se reemplaza por el índice no único idx_designacion_docente.
--    Primero se crea el índice nuevo: la FK necesita uno al quitar el UNIQUE.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion'
       AND INDEX_NAME = 'idx_designacion_docente') = 0,
    'CREATE INDEX idx_designacion_docente ON designacion (docente_id)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drops = (
    SELECT GROUP_CONCAT(DISTINCT CONCAT('DROP INDEX `', INDEX_NAME, '`') SEPARATOR ', ')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion'
      AND COLUMN_NAME = 'docente_id' AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY');
SET @sql = IF(@drops IS NULL, 'DO 0', CONCAT('ALTER TABLE designacion ', @drops));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 6. concurso.estado_concurso: se agregó el estado PUBLICADO (§2).
--    Hibernate crea la columna como ENUM y no la actualiza: sin esto,
--    el alta de concursos falla. Se agrega además el CHECK de §8.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concurso' AND COLUMN_NAME = 'estado_concurso'
       AND COLUMN_TYPE = 'enum(''PUBLICADO'',''EN_PROCESO'',''FINALIZADO'')') = 0,
    'ALTER TABLE concurso MODIFY estado_concurso ENUM(''PUBLICADO'',''EN_PROCESO'',''FINALIZADO'') NOT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concurso'
       AND CONSTRAINT_NAME = 'concurso_estado_concurso_valido') = 0,
    'ALTER TABLE concurso ADD CONSTRAINT concurso_estado_concurso_valido CHECK (estado_concurso IN (''PUBLICADO'',''EN_PROCESO'',''FINALIZADO''))',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 7. Motivo de la vacancia como lista (§1, §3.1) y profesor reemplazante.
--    ddl-auto agrega las columnas al iniciar el Servidor; se dejan acá
--    para las bases donde todavía no se levantó. Se usa VARCHAR y no el
--    ENUM nativo que genera Hibernate, así agregar un motivo nuevo no
--    requiere ALTER ... MODIFY (mismo problema que estado_concurso).
--    origen_vacancia sigue siendo el texto final que se muestra y hereda.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concurso' AND COLUMN_NAME = 'motivo_vacancia') = 0,
    'ALTER TABLE concurso ADD COLUMN motivo_vacancia VARCHAR(30) NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concurso' AND COLUMN_NAME = 'profesor_reemplazante') = 0,
    'ALTER TABLE concurso ADD COLUMN profesor_reemplazante VARCHAR(150) NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion' AND COLUMN_NAME = 'motivo_vacancia') = 0,
    'ALTER TABLE designacion ADD COLUMN motivo_vacancia VARCHAR(30) NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 8. designacion.profesor_reemplazante: el docente reemplazado también se
--    guarda en la designación (§4) para mostrarlo en Docente → Ver. Las
--    designaciones creadas desde un concurso lo heredan y las manuales lo
--    cargan en el formulario.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion'
       AND COLUMN_NAME = 'profesor_reemplazante') = 0,
    'ALTER TABLE designacion ADD COLUMN profesor_reemplazante VARCHAR(150) NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 9. alerta.concurso_id: vínculo de la alerta automática de concurso sin
--    cerrar (§10). Es un id plano, igual que designacion.concurso_id.
--    ddl-auto agrega la columna al iniciar el Servidor; se deja acá para
--    las bases donde todavía no se levantó.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerta' AND COLUMN_NAME = 'concurso_id') = 0,
    'ALTER TABLE alerta ADD COLUMN concurso_id BIGINT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 10. alerta.tipo: distingue las dos alertas automáticas de un concurso
--     (§10): CONCURSO_SIN_CERRAR y CONCURSO_HORAS_SIN_CUBRIR. Se guarda
--     como VARCHAR y no como ENUM nativo para poder agregar orígenes sin
--     ALTER ... MODIFY (mismo criterio que motivo_vacancia). ddl-auto la
--     agrega al iniciar el Servidor.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerta' AND COLUMN_NAME = 'tipo') = 0,
    'ALTER TABLE alerta ADD COLUMN tipo VARCHAR(30) NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 11. alerta.fecha_resolucion: fecha real en que la alerta quedó
--     resuelta (leída). La fija el Servidor al resolverla y se limpia si
--     vuelve a pendiente; las leídas anteriores a esta columna quedan en
--     NULL y el detalle no inventa la fecha. ddl-auto la agrega al
--     iniciar el Servidor.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerta' AND COLUMN_NAME = 'fecha_resolucion') = 0,
    'ALTER TABLE alerta ADD COLUMN fecha_resolucion DATE NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 12. cargo.tipo_cargo y docente.tipo: los cargos de preceptor solo
--     admiten preceptores (§3.2). Hibernate crea las columnas al arrancar
--     el Servidor; los registros previos quedan como DOCENTE. Correr este
--     paso después de ese primer arranque (si la columna no existe, no
--     hace nada).
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cargo' AND COLUMN_NAME = 'tipo_cargo') = 1,
    'UPDATE cargo SET tipo_cargo = ''DOCENTE'' WHERE tipo_cargo IS NULL OR tipo_cargo = ''''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'docente' AND COLUMN_NAME = 'tipo') = 1,
    'UPDATE docente SET tipo = ''DOCENTE'' WHERE tipo IS NULL OR tipo = ''''',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 12. certificado.presentado / fecha_emision / requiere_vencimiento:
--     atributos del modelo anterior (certificado por docente). Hoy el
--     certificado pertenece a una postulación (§3) y se renueva cargando
--     uno nuevo. Las columnas quedaron NOT NULL sin default y bloquean
--     toda carga con «Field 'presentado' doesn't have a default value»;
--     ddl-auto no las retira. Se eliminan las que existan en un solo ALTER.
-- ---------------------------------------------------------------------
SET @drops = (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN ', COLUMN_NAME) SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificado'
      AND COLUMN_NAME IN ('presentado', 'fecha_emision', 'requiere_vencimiento'));
SET @sql = IF(@drops IS NULL, 'DO 0', CONCAT('ALTER TABLE certificado ', @drops));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 13. tipo_certificado.meses_vigencia: atributo retirado del modelo (la
--     vigencia ahora se define por certificado y su fecha de vencimiento,
--     no por tipo). También quedó NOT NULL sin default e impide insertar
--     tipos de certificado.
-- ---------------------------------------------------------------------
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tipo_certificado'
       AND COLUMN_NAME = 'meses_vigencia') > 0,
    'ALTER TABLE tipo_certificado DROP COLUMN meses_vigencia',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
