-- =====================================================================
-- Datos ficticios para la base gestion_concursos (MySQL 8)
-- =====================================================================
-- Carga 5 filas por tabla con relaciones coherentes entre sí y con las
-- reglas de negocio de docs/REGLAS-DE-NEGOCIO.md.
--
-- Uso (con el Servidor DETENIDO para que Hibernate no interfiera):
--   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p `
--       gestion_concursos < docs\datos-ficticios.sql
--
-- ATENCIÓN: el paso 2 BORRA todos los datos actuales de las 14 tablas.
-- El script es re-ejecutable: limpiar + insertar siempre con los mismos ids.
--
-- Nota sobre "5 filas por tabla": persona es la tabla base de la herencia
-- JOINED de Docente y Usuario, así que lleva 10 filas (5 docentes + 5
-- usuarios) para que docente y usuario tengan 5 cada una.
--
-- Contenido:
--   5 cursos (1° a 5° año)      -> 5 materias (una por curso)
--   5 cargos (uno por materia)  -> 5 concursos (uno por cargo)
--   5 docentes + 5 usuarios     -> 5 postulaciones (una por concurso)
--   5 tipos de certificado      -> 5 certificados (uno por postulación)
--   5 designaciones (3 vigentes + 1 pendiente + 1 histórica)
--   5 licencias, 5 alertas, 5 reportes
--
-- Las fechas que alimentan alertas (vencimiento del certificado provisorio,
-- fin de la designación y cierre de un concurso sin cerrar) se calculan desde
-- CURDATE() y los mensajes se arman con CONCAT: así el demo respeta los
-- umbrales de AlertaService (3/7 días en certificados, 10/15 en designaciones
-- y 3/7 en concursos) y las fechas del mensaje coinciden con la entidad de
-- origen, sin importar cuándo se ejecute el script. El resto de las fechas
-- está anclado al ciclo lectivo 2026.
--
-- Credenciales de los usuarios cargados: contraseña "Elva1234" para todos
-- (hash BCrypt real de la app, listo para el encoder del Servidor).
-- =====================================================================

SET NAMES utf8mb4;

-- Fechas de referencia del demo (se recalculan en cada ejecución).
SET @hoy                  = CURDATE();
SET @vence_apto_4         = DATE_ADD(@hoy, INTERVAL 5 DAY);  -- certificado 4: MEDIA (<= 7 días)
SET @fin_designacion_1    = DATE_ADD(@hoy, INTERVAL 6 DAY);  -- designación 1: ALTA (<= 10 días)
SET @inicio_designacion_3 = DATE_ADD(@hoy, INTERVAL 12 DAY); -- manual PENDIENTE: comienza después
SET @fin_designacion_3    = DATE_ADD(@hoy, INTERVAL 377 DAY);
SET @cierre_concurso_3    = DATE_SUB(@hoy, INTERVAL 9 DAY);  -- concurso 3: ALTA (>= 7 días)


-- ---------------------------------------------------------------------
-- 1. Alineación de esquema
-- ---------------------------------------------------------------------
-- ddl-auto=update agrega columnas nuevas, pero no elimina las que el modelo
-- dejó de usar ni relaja sus NOT NULL. Las columnas heredadas de abajo
-- bloquean el alta de certificados y de tipos de certificado, así que se
-- retiran acá (mismo criterio que docs/migraciones.sql). Cada paso es
-- idempotente.

-- 1.1 certificado.fecha_carga: columna del modelo actual (la asigna el
--     Servidor y ordena el historial). ddl-auto la agrega al arrancar; se
--     crea acá para que el script funcione con el Servidor detenido.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificado'
       AND COLUMN_NAME = 'fecha_carga') = 0,
    'ALTER TABLE certificado ADD COLUMN fecha_carga DATE NULL AFTER caracter',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 certificado.fecha_emision / presentado / requiere_vencimiento:
--     atributos del modelo anterior (certificado por docente). NOT NULL sin
--     default: impiden insertar certificados.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificado'
       AND COLUMN_NAME = 'fecha_emision') > 0,
    'ALTER TABLE certificado DROP COLUMN fecha_emision',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificado'
       AND COLUMN_NAME = 'presentado') > 0,
    'ALTER TABLE certificado DROP COLUMN presentado',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'certificado'
       AND COLUMN_NAME = 'requiere_vencimiento') > 0,
    'ALTER TABLE certificado DROP COLUMN requiere_vencimiento',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 tipo_certificado.meses_vigencia: atributo retirado (la vigencia se
--     define por postulación). NOT NULL sin default: impide insertar tipos.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tipo_certificado'
       AND COLUMN_NAME = 'meses_vigencia') > 0,
    'ALTER TABLE tipo_certificado DROP COLUMN meses_vigencia',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 motivo_vacancia y profesor_reemplazante (§1 y §4): los agrega ddl-auto
--     al arrancar el Servidor; se crean acá para que el script funcione con el
--     Servidor detenido. Se usa VARCHAR y no el ENUM nativo de Hibernate, así
--     sumar un motivo nuevo no requiere ALTER ... MODIFY.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concurso'
       AND COLUMN_NAME = 'motivo_vacancia') = 0,
    'ALTER TABLE concurso ADD COLUMN motivo_vacancia VARCHAR(30) NULL AFTER origen_vacancia',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'concurso'
       AND COLUMN_NAME = 'profesor_reemplazante') = 0,
    'ALTER TABLE concurso ADD COLUMN profesor_reemplazante VARCHAR(150) NULL AFTER motivo_vacancia',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion'
       AND COLUMN_NAME = 'motivo_vacancia') = 0,
    'ALTER TABLE designacion ADD COLUMN motivo_vacancia VARCHAR(30) NULL AFTER origen_vacancia',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion'
       AND COLUMN_NAME = 'profesor_reemplazante') = 0,
    'ALTER TABLE designacion ADD COLUMN profesor_reemplazante VARCHAR(150) NULL AFTER motivo_vacancia',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.5 alerta.concurso_id / tipo / fecha_resolucion: columnas del modelo
--     actual (alertas automáticas de concurso y fecha real de resolución).
--     Mismo criterio que docs/migraciones.sql (pasos 9 a 11): ddl-auto las
--     agrega al arrancar el Servidor y acá quedan para poder cargar los datos
--     con el Servidor detenido.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerta'
       AND COLUMN_NAME = 'concurso_id') = 0,
    'ALTER TABLE alerta ADD COLUMN concurso_id BIGINT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerta'
       AND COLUMN_NAME = 'tipo') = 0,
    'ALTER TABLE alerta ADD COLUMN tipo VARCHAR(30) NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alerta'
       AND COLUMN_NAME = 'fecha_resolucion') = 0,
    'ALTER TABLE alerta ADD COLUMN fecha_resolucion DATE NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.6 designacion.cargo_id: FK de la relación unidireccional Cargo →
--     designaciones (§1). La entidad Designacion no expone el campo, así que
--     la columna solo la crea ddl-auto; se agrega acá para el mismo caso.
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designacion'
       AND COLUMN_NAME = 'cargo_id') = 0,
    'ALTER TABLE designacion ADD COLUMN cargo_id BIGINT NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------
-- 2. Limpieza (borra los datos actuales)
-- ---------------------------------------------------------------------
-- MySQL Workbench arranca con SQL_SAFE_UPDATES activo y rechaza cualquier
-- DELETE sin WHERE sobre una columna clave (error 1175). Por eso cada
-- borrado filtra por id (la PRIMARY KEY): el modo seguro queda como está y
-- la sentencia sigue borrando todas las filas.
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM alerta           WHERE id > 0;
DELETE FROM licencia         WHERE id > 0;
DELETE FROM certificado      WHERE id > 0;
DELETE FROM designacion      WHERE id > 0;
DELETE FROM concurso_docente WHERE id > 0;
DELETE FROM concurso         WHERE id > 0;
DELETE FROM cargo            WHERE id > 0;
DELETE FROM tipo_certificado WHERE id > 0;
DELETE FROM materia          WHERE id > 0;
DELETE FROM curso            WHERE id > 0;
DELETE FROM docente          WHERE id > 0;
DELETE FROM usuario          WHERE id > 0;
DELETE FROM persona          WHERE id > 0;
DELETE FROM reporte          WHERE id > 0;

SET FOREIGN_KEY_CHECKS = 1;


-- ---------------------------------------------------------------------
-- 3. Personas
-- ---------------------------------------------------------------------
-- Ids 1..5: docentes. Ids 6..10: usuarios del sistema.
INSERT INTO persona (id, eliminado, nombre, apellido, fecha_nacimiento, numero_documento, telefono, correo_electronico) VALUES
(1, 0, 'Laura',    'Giménez',  '1985-04-12', '30111222', '11-4567-8901', 'laura.gimenez@elva.edu.ar'),
(2, 0, 'Martín',   'Sosa',     '1979-09-03', '28444555', '11-4567-8902', 'martin.sosa@elva.edu.ar'),
(3, 0, 'Carolina', 'Ferreyra', '1990-01-25', '32666777', '11-4567-8903', 'carolina.ferreyra@elva.edu.ar'),
(4, 0, 'Diego',    'Bustos',   '1982-11-08', '29888999', '11-4567-8904', 'diego.bustos@elva.edu.ar'),
(5, 0, 'Ana',      'Ríos',     '1988-06-30', '31555444', '11-4567-8905', 'ana.rios@elva.edu.ar'),
(6, 0, 'Romina',   'Paredes',  '1983-03-17', '30666111', '11-4567-8911', 'romina.paredes@elva.edu.ar'),
(7, 0, 'Federico', 'Ledesma',  '1977-07-21', '27333222', '11-4567-8912', 'federico.ledesma@elva.edu.ar'),
(8, 0, 'Silvina',  'Castro',   '1991-12-05', '33777333', '11-4567-8913', 'silvina.castro@elva.edu.ar'),
(9, 0, 'Julián',   'Molina',   '1994-02-14', '35888444', '11-4567-8914', 'julian.molina@elva.edu.ar'),
(10, 0, 'Mariana', 'Duarte',   '1989-08-09', '32999555', '11-4567-8915', 'mariana.duarte@elva.edu.ar');


-- ---------------------------------------------------------------------
-- 4. Docentes (heredan persona 1..5)
-- ---------------------------------------------------------------------
INSERT INTO docente (id, activo, interno, area) VALUES
(1, 1, 1, 'Exactas'),
(2, 1, 1, 'Sociales'),
(3, 1, 0, 'Sociales'),
(4, 1, 1, 'Naturales'),
(5, 1, 0, 'Naturales');


-- ---------------------------------------------------------------------
-- 5. Usuarios del sistema (heredan persona 6..10)
-- ---------------------------------------------------------------------
-- contraseña = BCrypt de "Elva1234"
INSERT INTO usuario (id, nombre_usuario, `contraseña`, rol) VALUES
(6,  'rparedes', '$2a$10$3P.4kgoEY3ZQPsw2kC6rCubsmziy5NoiJYP4aalpPyhdybTmiJtWa', 'ADMIN'),
(7,  'fledesma', '$2a$10$3P.4kgoEY3ZQPsw2kC6rCubsmziy5NoiJYP4aalpPyhdybTmiJtWa', 'ADMIN'),
(8,  'scastro',  '$2a$10$3P.4kgoEY3ZQPsw2kC6rCubsmziy5NoiJYP4aalpPyhdybTmiJtWa', 'USER'),
(9,  'jmolina',  '$2a$10$3P.4kgoEY3ZQPsw2kC6rCubsmziy5NoiJYP4aalpPyhdybTmiJtWa', 'USER'),
(10, 'mduarte',  '$2a$10$3P.4kgoEY3ZQPsw2kC6rCubsmziy5NoiJYP4aalpPyhdybTmiJtWa', 'USER');


-- ---------------------------------------------------------------------
-- 6. Cursos y materias
-- ---------------------------------------------------------------------
INSERT INTO curso (id, eliminado, anio) VALUES
(1, 0, 1),
(2, 0, 2),
(3, 0, 3),
(4, 0, 4),
(5, 0, 5);

-- Una materia por curso; su área coincide con el área de los docentes
-- habilitados para el cargo correspondiente.
INSERT INTO materia (id, eliminado, nombre, area, curso_id) VALUES
(1, 0, 'Matemática',          'Exactas',   1),
(2, 0, 'Lengua y Literatura', 'Sociales',  2),
(3, 0, 'Historia',            'Sociales',  3),
(4, 0, 'Biología',            'Naturales', 4),
(5, 0, 'Física',              'Naturales', 5);


-- ---------------------------------------------------------------------
-- 7. Cargos (uno por materia: la relación Cargo-Materia es UNIQUE)
-- ---------------------------------------------------------------------
INSERT INTO cargo (id, eliminado, carga_horaria, descripcion, materia_id) VALUES
(1, 0, 20, 'Profesor de Matemática – 1° año',          1),
(2, 0, 16, 'Profesor de Lengua y Literatura – 2° año', 2),
(3, 0, 18, 'Profesor de Historia – 3° año',            3),
(4, 0, 12, 'Profesor de Biología – 4° año',            4),
(5, 0, 24, 'Profesor de Física – 5° año',              5);


-- ---------------------------------------------------------------------
-- 8. Tipos de certificado
-- ---------------------------------------------------------------------
INSERT INTO tipo_certificado (id, eliminado, codigo, nombre, obligatorio, permite_provisorio) VALUES
(1, 0, 'TITULO',  'Título habilitante',             1, 0),
(2, 0, 'APTO',    'Apto psicofísico',               1, 1),
(3, 0, 'CAPAC',   'Certificado de capacitación',    1, 0),
(4, 0, 'ANTEC',   'Antecedentes penales',           1, 0),
(5, 0, 'SEGURO',  'Seguro de responsabilidad civil', 0, 1);


-- ---------------------------------------------------------------------
-- 9. Concursos (uno por cargo)
-- ---------------------------------------------------------------------
-- Estados y fechas coherentes entre sí:
--   1 y 3 EN_PROCESO (apertura pasada; el 3 con el cierre ya vencido, por eso
--        sostiene la alerta CONCURSO_SIN_CERRAR)
--   2 y 5 FINALIZADO (el 2 sin horas libres; el 5 con 4 horas sin cubrir, por
--        eso sostiene la alerta CONCURSO_HORAS_SIN_CUBRIR)
--   4 PUBLICADO (cierre futuro)
-- motivo_vacancia es el motivo elegido de la lista (§1); origen_vacancia es el
-- texto final que se muestra y heredan las designaciones, compuesto como
-- "<etiqueta del motivo> – <detalle>" (solo la etiqueta si no hay detalle).
-- profesor_reemplazante es el docente reemplazado (texto libre, sin relación
-- con la tabla docente).
INSERT INTO concurso (id, eliminado, descripcion, estado_concurso, fecha_apertura, fecha_cierre,
                      cargo_id, caracter_designacion, es_rellamado, concurso_origen_id,
                      origen_vacancia, motivo_vacancia, profesor_reemplazante,
                      horas_totales, horas_transferidas) VALUES
(1, 0, 'Matemática 1° año',        'EN_PROCESO',
    '2026-03-02', '2026-11-30', 1, 'EFECTIVO',               0, NULL,
    'Licencia – del titular por cargo de mayor jerarquía', 'LICENCIA', 'Pérez, Marta', 20, 0),
(2, 0, 'Lengua y Literatura 2° año', 'FINALIZADO',
    '2026-02-02', '2026-06-30', 2, 'INTERINO',               0, NULL,
    'Renuncia – del titular', 'RENUNCIA', 'Sosa, Ricardo', 16, 0),
(3, 0, 'Historia 3° año',          'EN_PROCESO',
    '2026-04-06', @cierre_concurso_3, 3, 'REEMPLAZANTE',     0, NULL,
    'Licencia – anual del titular', 'LICENCIA', 'Medina, Laura', 18, 0),
(4, 0, 'Biología 4° año',          'PUBLICADO',
    '2026-10-05', '2026-12-20', 4, 'SUPLENTE',               0, NULL,
    'Jubilación – del titular', 'JUBILACION', 'Aguirre, Hugo', 12, 0),
(5, 0, 'Física 5° año',            'FINALIZADO',
    '2026-01-19', '2026-05-29', 5, 'SUPLENTE_CARGO_VACANTE', 0, NULL,
    'Creación de cargo', 'CREACION_CARGO', NULL, 24, 0);


-- ---------------------------------------------------------------------
-- 10. Postulaciones (una por concurso, con docentes distintos)
-- ---------------------------------------------------------------------
-- Horas elegidas <= horas_totales del concurso y sin exceder las disponibles.
-- El concurso 5 queda con 4 horas sin cubrir (24 totales − 20 elegidas): es la
-- causa de la alerta CONCURSO_HORAS_SIN_CUBRIR.
-- Coherencia estado <-> ganador (regla §9: el estado se deriva del ganador):
--   1, 2, 5 ganador=1 + DESIGNACION_DEFINITIVA
--   3       ganador=0 + DESIGNADO_PENDIENTE_POSESION (certificados aprobados)
--   4       ganador=0 + PENDIENTE_CERTIFICADOS
INSERT INTO concurso_docente (id, eliminado, fecha_emision, concurso_id, docente_id, ganador,
                              horas_elegidas, estado_postulacion, certificados_recibidos,
                              requiere_vencimiento_certificados, meses_vigencia, observacion) VALUES
(1, 0, '2026-03-10', 1, 1, 1, 12, 'DESIGNACION_DEFINITIVA',       1, 1, 12, NULL),
(2, 0, '2026-02-10', 2, 2, 1, 16, 'DESIGNACION_DEFINITIVA',       1, 1, 12, NULL),
(3, 0, '2026-04-12', 3, 3, 0, 18, 'DESIGNADO_PENDIENTE_POSESION', 1, 1,  6, 'Certificados aprobados; pendiente la toma de posesión.'),
(4, 0, '2026-10-08', 4, 4, 0,  6, 'PENDIENTE_CERTIFICADOS',       0, 1,  6, 'Pendiente la presentación del apto psicofísico.'),
(5, 0, '2026-01-22', 5, 5, 1, 20, 'DESIGNACION_DEFINITIVA',       1, 0, NULL, NULL);


-- ---------------------------------------------------------------------
-- 11. Designaciones
-- ---------------------------------------------------------------------
-- 1, 2 y 5: nacidas de un concurso (cargo_id y concurso_id vinculados; el
--       carácter, el origen, el motivo y el profesor reemplazante coinciden con
--       los del concurso). ACTIVO = vigente con fecha de fin: la 1 finaliza
--       dentro del umbral de 10 días y sostiene la alerta ALTA de designación;
--       la 2 y la 5 quedan fuera de rango.
-- 3: manual y PENDIENTE (comienza más adelante), sin cargo_id ni concurso_id
--    (§4, alta manual).
-- 4: histórica y FINALIZADA, sin cargo_id, sin concurso_id y sin profesor
--    reemplazante, para cubrir el caso vacío de la columna en Docente → Ver.
INSERT INTO designacion (id, eliminado, fecha_inicio_efectiva, fecha_fin_definitiva, origen_vacancia,
                         motivo_vacancia, profesor_reemplazante, concurso_id, caracter_designacion,
                         estado_designacion, docente_id, cargo_id) VALUES
(1, 0, '2026-03-15', @fin_designacion_1, 'Licencia – del titular por cargo de mayor jerarquía',
    'LICENCIA', 'Pérez, Marta',   1, 'EFECTIVO',     'ACTIVO',     1, 1),
(2, 0, '2026-03-01', '2027-06-30', 'Renuncia – del titular',
    'RENUNCIA', 'Sosa, Ricardo',  2, 'INTERINO',     'ACTIVO',     2, 2),
(3, 0, @inicio_designacion_3, @fin_designacion_3, 'Licencia – por estudios del titular',
    'LICENCIA', 'Medina, Laura', NULL, 'REEMPLAZANTE', 'PENDIENTE', 3, NULL),
(4, 0, '2024-02-01', '2024-11-30', 'Licencia – por maternidad de la titular',
    'LICENCIA', NULL,           NULL, 'SUPLENTE',     'FINALIZADO', 4, NULL),
(5, 0, '2026-06-01', '2027-05-31', 'Creación de cargo',
    'CREACION_CARGO', NULL,       5, 'SUPLENTE_CARGO_VACANTE', 'ACTIVO', 5, 5);


-- ---------------------------------------------------------------------
-- 12. Certificados (uno por postulación)
-- ---------------------------------------------------------------------
-- PROVISORIO siempre con fecha_vencimiento; el definitivo la deja NULL.
-- El 4 vence dentro de los 7 días y sostiene la alerta MEDIA de certificado;
-- el 2 vence lejos (fuera del umbral) y el resto son definitivos.
INSERT INTO certificado (id, eliminado, concurso_docente_id, tipo_certificado_id, caracter,
                         fecha_vencimiento, fecha_carga, actual) VALUES
(1, 0, 1, 1, 'DEFINITIVO', NULL,                '2026-03-10', 1),
(2, 0, 2, 2, 'PROVISORIO', DATE_ADD(@hoy, INTERVAL 60 DAY), '2026-02-10', 1),
(3, 0, 3, 3, 'DEFINITIVO', NULL,                '2026-04-12', 1),
(4, 0, 4, 2, 'PROVISORIO', @vence_apto_4,       '2026-10-08', 1),
(5, 0, 5, 1, 'DEFINITIVO', NULL,                '2026-01-22', 1);


-- ---------------------------------------------------------------------
-- 13. Licencias (asociadas a designaciones vigentes)
-- ---------------------------------------------------------------------
-- Todas caen dentro del período de la designación a la que pertenecen; las
-- designaciones 3 (PENDIENTE) y 4 (FINALIZADA) quedan sin licencias.
INSERT INTO licencia (id, eliminado, tipo, fecha_inicio, fecha_fin, renovable, designacion_id) VALUES
(1, 0, 'Licencia por enfermedad', '2026-05-04', '2026-05-15', 0, 1),
(2, 0, 'Licencia por estudio',    '2026-06-01', '2026-06-30', 1, 2),
(3, 0, 'Licencia por trámite',    '2026-08-10', '2026-08-12', 0, 1),
(4, 0, 'Licencia por enfermedad', '2026-07-06', '2026-07-17', 0, 5),
(5, 0, 'Licencia por vacaciones', '2027-01-05', '2027-01-25', 1, 2);


-- ---------------------------------------------------------------------
-- 14. Alertas
-- ---------------------------------------------------------------------
-- Una alerta por causa (§10) y cada mensaje con el formato de AlertaService,
-- usando las mismas fechas que la entidad de origen:
--   1 certificado 4 (provisorio, vence en 5 días)  -> MEDIA, sin designación
--   2 designación 1 (finaliza en 6 días)           -> ALTA
--   3 concurso 3 (cierre vencido hace 9 días)      -> ALTA, CONCURSO_SIN_CERRAR
--   4 concurso 5 (cerrado con 4 horas libres)      -> MEDIA, CONCURSO_HORAS_SIN_CUBRIR
--   5 manual y ya leída: conserva la fecha de resolución y no tiene origen
INSERT INTO alerta (id, eliminado, mensaje, fecha_emision, leida, fecha_resolucion,
                    prioridad, concurso_id, tipo, designacion_id, certificado_id) VALUES
(1, 0,
    CONCAT('El apto psicofísico provisorio de Bustos, Diego (Concurso Biología 4° año) vence el ',
           DATE_FORMAT(@vence_apto_4, '%d/%m/%Y'), '.'),
    @hoy, 0, NULL, 'MEDIA', NULL, NULL, NULL, 4),
(2, 0,
    CONCAT('La designación de Giménez, Laura (Cargo: Profesor de Matemática – 1° año · Matemática) finaliza el ',
           DATE_FORMAT(@fin_designacion_1, '%d/%m/%Y'),
           ' (faltan ', DATEDIFF(@fin_designacion_1, @hoy), ' días).'),
    @hoy, 0, NULL, 'ALTA', NULL, NULL, 1, NULL),
(3, 0,
    CONCAT('El concurso «Historia 3° año» sigue en proceso: la fecha de cierre fue el ',
           DATE_FORMAT(@cierre_concurso_3, '%d/%m/%Y'),
           ' (hace ', DATEDIFF(@hoy, @cierre_concurso_3), ' días).'),
    @hoy, 0, NULL, 'ALTA', 3, 'CONCURSO_SIN_CERRAR', NULL, NULL),
(4, 0,
    'El concurso «Física 5° año» quedó cerrado con 4 horas sin cubrir.',
    @hoy, 0, NULL, 'MEDIA', 5, 'CONCURSO_HORAS_SIN_CUBRIR', NULL, NULL),
(5, 0,
    'Se revisó el cronograma de concursos con la secretaría académica.',
    DATE_SUB(@hoy, INTERVAL 20 DAY), 1, DATE_SUB(@hoy, INTERVAL 13 DAY),
    'MEDIA', NULL, NULL, NULL, NULL);


-- ---------------------------------------------------------------------
-- 15. Reportes
-- ---------------------------------------------------------------------
INSERT INTO reporte (id, eliminado, titular, fecha) VALUES
(1, 0, 'Reporte de designaciones activas – ciclo 2026', '2026-09-01'),
(2, 0, 'Reporte de concursos en proceso',               '2026-09-15'),
(3, 0, 'Reporte de horas asignadas por cargo',          '2026-10-01'),
(4, 0, 'Reporte de certificados vigentes',              '2026-10-10'),
(5, 0, 'Reporte de licencias en curso',                 '2026-10-20');


-- ---------------------------------------------------------------------
-- 16. Verificación
-- ---------------------------------------------------------------------
SELECT 'persona' AS tabla, COUNT(*) AS filas FROM persona
UNION ALL SELECT 'docente',          COUNT(*) FROM docente
UNION ALL SELECT 'usuario',          COUNT(*) FROM usuario
UNION ALL SELECT 'curso',            COUNT(*) FROM curso
UNION ALL SELECT 'materia',          COUNT(*) FROM materia
UNION ALL SELECT 'cargo',            COUNT(*) FROM cargo
UNION ALL SELECT 'concurso',         COUNT(*) FROM concurso
UNION ALL SELECT 'concurso_docente', COUNT(*) FROM concurso_docente
UNION ALL SELECT 'designacion',      COUNT(*) FROM designacion
UNION ALL SELECT 'certificado',      COUNT(*) FROM certificado
UNION ALL SELECT 'tipo_certificado', COUNT(*) FROM tipo_certificado
UNION ALL SELECT 'licencia',         COUNT(*) FROM licencia
UNION ALL SELECT 'alerta',           COUNT(*) FROM alerta
UNION ALL SELECT 'reporte',          COUNT(*) FROM reporte;

-- Alertas cargadas: origen y estado (§10, una alerta por causa).
-- Esperado: MEDIA por certificado 4, ALTA por designación 1, ALTA por el
-- concurso 3 sin cerrar, MEDIA por las 4 horas del concurso 5 y la manual leída.
SELECT id, prioridad, tipo, leida, fecha_resolucion FROM alerta ORDER BY id;

-- Horas por concurso: lo elegido nunca supera lo total y el 5 queda con 4
-- libres (causa de la alerta CONCURSO_HORAS_SIN_CUBRIR).
SELECT c.id, c.horas_totales, COALESCE(SUM(cd.horas_elegidas), 0) AS elegidas,
       c.horas_totales - COALESCE(SUM(cd.horas_elegidas), 0) AS disponibles
FROM concurso c
LEFT JOIN concurso_docente cd ON cd.concurso_id = c.id AND cd.eliminado = 0
GROUP BY c.id, c.horas_totales
ORDER BY c.id;
