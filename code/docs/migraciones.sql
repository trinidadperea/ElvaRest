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
-- 7. cargo.tipo_cargo y docente.tipo: los cargos de preceptor solo
--    admiten preceptores (§3.2). Hibernate crea las columnas al arrancar
--    el Servidor; los registros previos quedan como DOCENTE. Correr este
--    paso después de ese primer arranque (si la columna no existe, no
--    hace nada).
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
