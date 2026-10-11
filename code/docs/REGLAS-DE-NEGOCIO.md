# Informe de Reglas de Negocio — ElvaRest

**Alcance**: Concursos, Cargos, Designaciones, Docentes y flujo completo de cobertura de cargos
**Fuentes**: código vigente verificado (`ConcursoController`, `FlujoConcursoService`, `DesignacionController`, `CargoController`, `DocenteController` del Cliente; entidades/enums del Servidor; validación de bajas: `preDelete` + `validarEliminacion` del Servidor) + registro de decisiones tomadas durante el desarrollo.

---

## 1. Modelo de datos y relaciones clave

- Cadena principal: **Concurso → Cargo → Designación**. Un `Cargo` está vinculado a una única `Materia`, y esta a un único `Curso`; por lo tanto, "mismo cargo" implica "misma materia y curso" y no hace falta compararlos por separado.
- `Concurso.caracterDesignacion` = carácter **previsto** (nullable para concursos históricos; se completa editando el concurso).
- `Designacion.caracterDesignacion` = dato propio de la designación (la hereda del concurso en el flujo de concurso; las designaciones manuales conservan su propio selector).
- Una **Designación está asociada a un único docente** (relación `@ManyToOne`, sin restricción de unicidad en BD).
- `Concurso` persiste `esRellamado` y `concursoOrigenId` (vínculo de rellamado, no solo en memoria).
- `Concurso` persiste su **vínculo con el cargo** (`cargo_id`), obligatorio en el alta directa. La relación **Cargo → Designación es unidireccional**: el FK `cargo_id` vive en la tabla `designacion`, pero la entidad `Designacion` no expone campo de cargo (evita ciclos JPA/Jackson).
- `ConcursoDocente` (postulación) persiste todo el flujo: `horasElegidas` (columna `horas_elegidas`), `ganador`, `estadoPostulacion` (ENUM `estado_postulacion`), los datos de certificados (`certificadosRecibidos`, `requiereVencimientoCertificados`, `mesesVigencia`) y `observacion`.
- `Concurso` persiste además `horasTotales` (horas configuradas del concurso) y `horasTransferidas` (horas remanentes transferidas a un rellamado). No se persiste "horasDisponibles": es derivable (total − asignadas − transferidas).

## 2. Estados y ciclo de vida

**Concurso** (`EstadoConcurso`): `PUBLICADO` → `EN_PROCESO` → `FINALIZADO`
- Todo concurso **nace PUBLICADO**; no existe selector manual de estado en formularios.
- **Transición automática**: un `PUBLICADO` pasa a `EN_PROCESO` cuando llega su fecha (persistida como `fechaCierre`); se aplica al primer acceso (listar/detallar) y se persiste; si falla la persistencia, se refleja igual en pantalla y se reintenta.
- Pasa a `FINALIZADO`: por cierre manual (POST cerrar) o por **cierre automático** (ver §3.6–3.7).

**Postulación** (`EstadoPostulacion`): ciclo `PENDIENTE_CERTIFICADOS` → `DESIGNADO_PENDIENTE_POSESION` → `DESIGNACION_DEFINITIVA`, con salida a `CANCELADO_INEFECTIVO`.
- Estados cancelados dejan de reservar horas (liberan cupo).
- El estado se **persiste en el Servidor** (enum `estado_postulacion`) y sobrevive a los reinicios del Cliente; los registros históricos sin estado derivan del ganador y su tratamiento conservador se detalla en §9.

**Designación** (`EstadoDesignacion`): `ACTIVO`, `PENDIENTE`, `FINALIZADO`. Vigencia = **ACTIVO o PENDIENTE** (FINALIZADO no vigente).

**Carácter de designación** (`CaracterDesignacion`): EFECTIVO, INTERINO, REEMPLAZANTE, SUPLENTE, SUPLENTE_CARGO_VACANTE.

## 3. Reglas de Concursos (flujo completo)

**3.1 Alta / edición**
- Descripción obligatoria. Alta directa (**Concursos → "Nuevo concurso"**, con cargo **seleccionable**) exige: cargo vacante, fecha y hora del concurso, carácter de designación y **origen de la vacancia** (texto libre, obligatorio en el alta y editable en la edición).
- `fechaCierre` se deriva de la fecha/hora del concurso; `fechaApertura` = hoy si no se indica.
- Horas totales: las indicadas, o la carga horaria del cargo como respaldo; si no hay ninguna → error ("indique las horas totales o complete la carga horaria del cargo").
- En edición se preservan estado, apertura y cierre actuales si el formulario no los envía.
- **Alta con cargo fijado por contexto**: desde Cargo → Ver → "Abrir concurso para este cargo" el formulario llega con el cargo preseleccionado y **no editable** (el POST lo conserva).

**3.2 Postulación / toma de horas** (concurso abierto)
- Docente obligatorio y **activo**.
- Rechaza si el docente **ya tiene toma de horas vigente en este concurso**.
- **Exclusión por reemplazo propio** (regla general): no puede postularse quien tiene una **designación ACTIVA vigente en el mismo cargo** (no se selecciona a sí mismo como reemplazo).
- **Exclusión en rellamados**: no puede volver a seleccionarse quien **ya obtuvo designación efectiva en el concurso origen** para ese cargo.
- Horas: **mínimo 1** y **máximo las horas disponibles** del concurso.
- Al registrar: `ganador=false`, fecha de emisión = hoy, estado inicial `PENDIENTE_CERTIFICADOS`.
- **Doble barrera**: la exclusión se aplica tanto en el selector (GET) como en la validación del POST.

**3.3 Modificación de horas**
- Solo con el concurso abierto; solo para postulaciones **pendientes** (certificados o posesión); horas ≥ 1 y ≤ máximo disponible.

**3.4 Validación de certificados** (concurso abierto, postulación `PENDIENTE_CERTIFICADOS`)
- Decisión obligatoria: Aprobar o Rechazar.
- **Aprobar** requiere marcar certificados recibidos; si exige vencimiento, meses ≥ 1 (si falta, se re-renderiza el formulario conservando lo cargado para corregir). Resultado → `DESIGNADO_PENDIENTE_POSESION`.
- **Rechazar** → `CANCELADO_INEFECTIVO` y **restitución de las horas** reservadas al concurso.

**3.5 Toma de posesión** (postulación `DESIGNADO_PENDIENTE_POSESION`)
- Fechas de inicio efectiva y fin definitiva obligatorias (defaults: hoy y +1 año).
- El **concurso debe tener carácter definido** (si no, error pidiendo completarlo).
- La designación **hereda el carácter del concurso** y el **origen de la vacancia** capturado en él (los concursos anteriores al campo usan el respaldo `"Concurso: <descripción>"`); estado = **ACTIVO**; la fecha de fin cargada en el formulario se persiste directamente como `fechaFinDefinitiva`; guarda además el **vínculo directo** con el concurso (`concursoId`).
- **Validación de vigencia**: se rechaza si el cargo ya tiene una designación ACTIVA/PENDIENTE del mismo docente ("El docente ya posee una designación vigente para este cargo.").
- La designación se crea y se agrega al cargo; la postulación queda `ganador=true` → `DESIGNACION_DEFINITIVA`.

**3.6 Declarar inefectivo** (solo `DESIGNADO_PENDIENTE_POSESION`)
- → `CANCELADO_INEFECTIVO` con observación (default: "No se presentó a la toma de posesión") y **liberación de horas**.

**3.7 Cierre del concurso**
- Manual: prohibido cerrar si hay docentes **pendientes de validación de certificados**; al cerrar con horas libres avisa que puede abrirse un rellamado.
- **Automático**: al confirmar una posesión, si **todas las postulaciones vigentes quedaron con designación definitiva y no quedan horas disponibles**, el concurso pasa solo a FINALIZADO.

**3.8 Rellamado** (solo desde concurso **cerrado** con horas remanentes > 0)
- Crea un nuevo concurso PUBLICADO que: hereda **cargo, carácter y origen de la vacancia del origen** (sin selector), recibe las **horas remanentes** como totales, marca `esRellamado=true` y `concursoOrigenId`.
- Las horas remanentes se **transferen**: se persisten como `horasTransferidas` en el origen y este queda con 0 horas disponibles, incluso tras reiniciar el Cliente.
- Descripción por defecto: "Rellamado – <origen>".

**3.9 Flujos legacy** (se mantienen para enlaces existentes)
- **Ganador**: solo en concurso FINALIZADO; marca una postulación como ganadora.
- **Agregar cargo**: solo FINALIZADO y con ganador elegido; crea la designación con fechas de inicio y fin definitiva y carácter **manuales** (select propio) y estado inicial PENDIENTE; hereda el origen de la vacancia del concurso y su vínculo directo (`concursoId`). *No aplica la validación de designación vigente.*

**3.10 Cálculo de horas** (reglas de `FlujoConcursoService`)
- Totales: DTO persistido → carga horaria del cargo (respaldo).
- Asignadas: suma de horas de postulaciones **vigentes** (excluye canceladas y sin horas).
- Disponibles: **total − asignadas − transferidas a un rellamado**, nunca menor a 0.
- Listo para cierre: sin canceladas pendientes, todas definitivas y disponibles ≤ 0.

## 4. Reglas de Designaciones

- **Múltiples designaciones históricas permitidas** para el mismo docente y cargo (no es duplicado).
- **Única prohibición**: dos designaciones **vigentes simultáneas** (ACTIVO/PENDIENTE) para el mismo docente y cargo. Se valida por **lógica de negocio**, nunca con UNIQUE(docente_id, cargo_id) (bloquearía las históricas).
- **La vigencia se determina por estado, NO por fechas** (decisión explícita). `fechaFinDefinitiva` no define vigencia.
- Al modificar una designación (formulario manual) se **conservan sus alertas y licencias** asociadas.
- **Navegación con contexto**: editada desde Cargo → Ver, tanto **Cancelar** como el **guardado exitoso** vuelven al detalle de ese cargo (mecanismo `cargoId`); desde otros accesos, el comportamiento es el original (lista general).
- Cada designación creada desde un concurso guarda su **vínculo directo** con él (`concursoId`) y el origen de la vacancia heredado (snapshot); las designaciones manuales e históricas (sin vínculo) se resuelven por el texto `origenVacancia`.
- Sin restricción UNIQUE en BD: se reemplazó por índice no único (`idx_designacion_docente`) para permitir el historial.
- **Alta manual** (Designaciones → Nueva designación): el formulario **no tiene selector de cargo** y la designación se persiste con `cargo_id = NULL`; el `cargoId` del formulario es solo contexto de navegación (volver al cargo). Consecuencias: no aparece en Cargo → Ver, **no suma en "Designaciones activas" ni en Vacante/Ocupado** de ningún cargo, y el detalle del docente muestra "—" en Materia/Curso.
- Las designaciones creadas por el **flujo de concursos** (posesión / ganador legacy) sí quedan **vinculadas al cargo** (se agregan a su colección) y son las que cuentan en el cargo.

## 5. Reglas de Cargos

- **Carga Horaria en Cargo → Ver** = **horas que el docente tomó en el concurso** (no la carga total del cargo). Se resuelve por el **vínculo directo** `concursoId` → postulación del mismo docente → horas efectivas; las designaciones históricas (sin vínculo) conservan el respaldo `origenVacancia = "Concurso: <desc>"`; si no resuelve, **fallback** a la carga del cargo.
- Un cargo agrupa sus designaciones; desde ahí se accede a editar cada una (conservando contexto).
- La designación vigente de un cargo es la base de la **exclusión de docentes** en concursos de ese cargo.
- **Listado de cargos — columna "Designaciones activas"**: cuenta las designaciones **no eliminadas** con estado **ACTIVO** de cada cargo (mismo criterio que la tarjeta "Designaciones activas" del detalle del cargo). No se cuentan las `FINALIZADO`, las `PENDIENTE` ni las eliminadas; destacar que **"activa" (solo `ACTIVO`) no es lo mismo que "vigente" (ACTIVO o PENDIENTE)**. El cálculo se resuelve en la **fuente de datos** (`CargoController` del Cliente), no en la vista.
- **Estado del cargo (derivado)**: el listado de cargos **ya no muestra la columna "Estado"** (cambio solo visual). El cálculo de Vacante/Ocupado se mantiene intacto y se sigue mostrando como badge en el detalle del cargo: **Vacante** = sin designación ACTIVO/PENDIENTE; **Ocupado** = con al menos una vigente.
- El botón "Nueva designación" está **oculto en Cargo → Ver** (cabecera y estado vacío); el alta manual se realiza exclusivamente desde el módulo **Designaciones**.

## 6. Reglas de Docentes

1. **Regla general** (todo concurso): excluir docentes con designación **ACTIVA** vigente en el mismo cargo del concurso (selector + POST).
2. **Regla de rellamados** (acumulativa): excluir además a quienes obtuvieron **designación efectiva en el concurso origen**, reconocidas por su **vínculo directo** (`concursoId`) con el texto `"Concurso: <desc>"` como respaldo histórico (resuelto desde el DTO persistido; *fail-open* si no se puede determinar el origen).
- No se excluye por designaciones de otros cargos ni a docentes sin designación efectiva.
- En el selector también quedan fuera docentes con toma de horas vigente en el mismo concurso.
- **Historial de concursos del docente** (detalle): solo se listan los concursos con **designación definitiva** concretada (cualquier estado del concurso) o con **postulación vigente** en concurso `PUBLICADO`/`EN_PROCESO` (etapa en la que todavía puede concretarse la designación). Se excluyen los concursos finalizados o cancelados sin designación definitiva. El filtro se aplica en la **fuente de datos** (`DocenteController`), no en la vista.

## 7. Reglas de eliminación de entidades

La baja lógica de **Concursos, Docentes, Cargos, Cursos y Materias** se valida en el **Servidor antes de ejecutarse**: una petición directa al endpoint DELETE también se rechaza (HTTP 400 con el mensaje de la regla) y el registro rechazado no se modifica ni pierde sus relaciones. El botón de eliminar permanece visible; el Cliente muestra el mensaje del Servidor como alerta en la lista, sin mensaje de éxito.

- **Concurso EN_PROCESO**: se rechaza si tiene pendientes — una postulación vigente sin ganador (pendiente de certificados) o una designación **PENDIENTE** vinculada al concurso (pendiente de toma de posesión; vínculo por `concursoId` o texto “Concurso: <desc>” en históricas).
- **Concurso FINALIZADO**: se rechaza si tiene designaciones **ACTIVO** vinculadas (efectivas que continúan activas).
- **Docente**: se rechaza si tiene designaciones **ACTIVO**, designaciones **PENDIENTE** o postulaciones vigentes sin ganador, en cualquier concurso.
- **Cargo**: se rechaza si tiene designaciones **ACTIVO** (criterio de “activa” de §5: no eliminadas y estado ACTIVO, sobre todas las designaciones del cargo).
- **Curso**: se rechaza si está asociado a una **materia** (no eliminada) o a un **cargo** (a través de la materia del cargo).
- **Materia**: se rechaza si está asociada a un **cargo**.
- Mensajes: “No se puede eliminar el concurso porque tiene designaciones pendientes de certificados o de toma de posesión.” / “…porque tiene designaciones efectivas que continúan activas.”; “No se puede eliminar el docente porque tiene designaciones activas asociadas.” / “…porque tiene designaciones pendientes de certificados o de toma de posesión.”; “No se puede eliminar el cargo porque tiene designaciones activas asociadas.”; “No se puede eliminar el curso porque está asociado a una materia.” / “…a un cargo.”; “No se puede eliminar la materia porque está asociada a un cargo.”
- La condición de "pendiente" usa el estado persistido: cuenta como pendiente toda postulación vigente **sin ganador y no cancelada** (`estadoPostulacion != CANCELADO_INEFECTIVO`); las postulaciones canceladas (rechazadas o declaradas inefectivas) **ya no bloquean** la eliminación. Los registros antiguos **sin estado** se tratan de forma conservadora (ver §9).

## 8. Reglas transversales de UI / integridad

- Sin selector manual de estado de concurso; sin selector de carácter en rellamados (herencia no editable).
- Eliminación de botones redundantes: se quitó "Ver" en la tabla de designaciones del cargo y "Ver detalle" en la edición de designación (las vistas/endpoints de detalle se conservan para otros accesos).
- `estado_concurso` alineado a nivel BD: ENUM('PUBLICADO','EN_PROCESO','FINALIZADO') + CHECK `concurso_estado_concurso_valido` (los ALTER no los hace `ddl-auto`).
- Atributos retirados del modelo y de la BD: `Docente.puntaje`, `Designacion.fechaFinEstimada` y `Curso.seccion` (con sus referencias en DTOs, formularios, mapeos y vistas). Las columnas físicas `docente.puntaje`, `designacion.fecha_fin_estimada` y `curso.seccion` se retiraron con ALTER explícito, ya que `ddl-auto` no elimina columnas. El detalle de designación muestra solo **fecha de inicio efectiva** y **fecha de fin definitiva**.
- Detalle del docente (vista): secciones en orden **Designaciones registradas → Historial de concursos**; la tabla de designaciones muestra **Materia/Curso** (materia y curso del cargo) y **Fecha fin definitiva** (vacía si no existe), ya **no muestra la columna "Origen de vacancia"** (cambio solo visual: el atributo sigue en el modelo/BD y se usa en el flujo de concursos y designaciones) y no tiene botón "Ver" (esas designaciones se consultan vía Cargo → Ver).
- Detalle del docente — acciones: sin botones finales "Agregar certificado" y "Editar docente" (las vistas y endpoints se conservan para otros accesos).
- Formularios de docente: el alta **no tiene sección "Opciones"** y crea al docente **activo** por defecto (campo oculto); en la edición **Activo** e **Interno** no son editables — al guardar se **preservan sus valores actuales** (campos ocultos). El listado muestra "Interno" junto al nombre y la columna Estado (Activo/Inactivo).
- Los datos del flujo (**estado de postulación, certificados/observación, horas totales y transferidas, `esRellamado`/`concursoOrigenId`**) se persisten en el Servidor; el store temporal del Cliente (`PostulacionDatosStore`) fue **eliminado** y todas las lecturas/escrituras pasan por el DTO.

## 9. Limitaciones / deuda conocida (reglas al momento actual)

- `agregarCargo` (legacy) **no aplica** la validación de designación vigente ni la herencia de carácter (tiene su propio select).
- Designaciones **históricas** sin horas de concurso resueltas muestran el fallback (carga del cargo).
- La exclusión de rellamado es **fail-open** si no logra resolver el origen.
- Los registros antiguos **sin estado de postulación** derivan su estado del ganador (`ganador ? DESIGNACION_DEFINITIVA : PENDIENTE_CERTIFICADOS`) y en las validaciones de eliminación se tratan de forma conservadora (bloquean la baja).
- Las transferencias de horas hechas **antes** de esta migración que solo vivían en memoria no son recuperables (mismo comportamiento que un reinicio previo del Cliente); los concursos viejos sin `horasTotales` siguen resolviendo sus horas totales por la carga del cargo.
