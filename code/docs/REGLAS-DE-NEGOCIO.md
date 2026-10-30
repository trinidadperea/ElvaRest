# Informe de Reglas de Negocio — ElvaRest

**Alcance**: Concursos, Cargos, Designaciones, Docentes y flujo completo de cobertura de cargos
**Fuentes**: código vigente verificado (`ConcursoController`, `FlujoConcursoService`, `DesignacionController`, `CargoController`, `DocenteController` del Cliente; entidades/enums del Servidor; validación de bajas: `preBaja` + `validarEliminacion` del Servidor; validación de postulaciones y designaciones: `ConcursoDocenteService` y `DesignacionService` del Servidor) + registro de decisiones tomadas durante el desarrollo.

---

## 1. Modelo de datos y relaciones clave

- Cadena principal: **Concurso → Cargo → Designación**. Un `Cargo` está vinculado a una única `Materia`, y esta a un único `Curso`; por lo tanto, "mismo cargo" implica "misma materia y curso" y no hace falta compararlos por separado.
- `Concurso.caracterDesignacion` = carácter **previsto** (nullable para concursos históricos; se completa editando el concurso).
- `Designacion.caracterDesignacion` = dato propio de la designación (la hereda del concurso en el flujo de concurso; las designaciones manuales conservan su propio selector).
- **Motivo de la vacancia** (`MotivoVacancia`: `RENUNCIA`, `CREACION_CARGO`, `LICENCIA`, `JUBILACION`, `OTRO`): se persiste en `Concurso.motivoVacancia` y en `Designacion.motivoVacancia` (nullable, porque los registros históricos solo tienen el texto).
- **`origenVacancia` es el texto final** (se muestra, se hereda a las designaciones y alimenta el PDF): se arma como `<etiqueta del motivo> – <detalle>` y, con «Otro», guarda solo el texto libre. La composición y la descomposición viven en el enum `MotivoVacancia` del Cliente (`texto`, `desdeTexto`, `detalleDesdeTexto`), de modo que el resto de los módulos no cambia. La composición es **idempotente**: si el detalle ya repite el motivo («Renuncia» o «Renuncia del titular») se recorta antes de componer, así nunca se guarda «Renuncia – Renuncia».
- `Concurso.profesorReemplazante` = nombre del **docente que se reemplaza** (docente reemplazado). Se persiste también en `Designacion.profesorReemplazante` (heredado del concurso o cargado a mano) para mostrarlo en el detalle de la designación y en Docente → Ver. Es un dato **informativo de texto libre**: no se asocia a la entidad `Docente`, no genera designaciones y no participa de ninguna validación.
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
- Descripción obligatoria. Alta directa (**Concursos → "Nuevo concurso"**, con cargo **seleccionable**) exige: cargo vacante, fecha y hora del concurso, carácter de designación y el **motivo de la vacancia** (lista `MotivoVacancia`).
- **Validaciones de alta en el Servidor**: `ConcursoService.alta`/`preAlta` exigen descripción, cargo, fecha del concurso y carácter de designación, y **todo concurso nace `PUBLICADO`** (el estado que traiga el pedido se ignora), así que un `POST /concursos` directo no puede crear un concurso incompleto. El **motivo de la vacancia** se sigue validando y componiendo en el Cliente (`aplicarMotivoVacancia`), porque el texto compuesto es el que se persiste en `origenVacancia`.
- **Motivo + detalle**: el desplegable define el motivo y el campo **Detalle del motivo** lo enriquece; el detalle es **opcional** para los motivos de la lista y **obligatorio** al elegir «Otro». Se guarda compuesto (`Renuncia – por traslado`) o, con «Otro», solo el texto escrito. El campo de detalle queda **siempre visible** y `app.js` solo alterna su obligatoriedad (`data-required-when`). El detalle **no repite** el motivo: la composición recorta la etiqueta si llega incluida y, al editar, el formulario muestra solo el detalle (`Renuncia del titular` → `del titular`).
- **Profesor reemplazante**: campo **opcional** de texto libre (hasta 150 caracteres) con el nombre del docente reemplazado; se puede editar en cualquier momento y es independiente del estado del concurso.
- El **PDF del concurso** (`ReporteService`) incluye el origen de la vacancia y el profesor reemplazante (con «-» cuando está vacío).
- `fechaCierre` se deriva de la fecha/hora del concurso; `fechaApertura` = hoy si no se indica (el Servidor la completa en el alta).
- Horas totales: las indicadas, o la carga horaria del cargo como respaldo; si no hay ninguna → error ("indique las horas totales o complete la carga horaria del cargo"). El **Servidor las persiste** con el concurso: no se derivan al leer, así que un cambio posterior en la carga del cargo no reescribe la historia de los concursos ya creados.
- En edición se preservan estado, apertura y cierre actuales si el formulario no los envía.
- **Alta con cargo fijado por contexto**: desde Cargo → Ver → "Abrir concurso para este cargo" el formulario llega con el cargo preseleccionado y **no editable** (el POST lo conserva).
- **Un solo concurso abierto por cargo**: un cargo no puede tener dos concursos **abiertos** a la vez. "Abierto" se define por el **estado** (`PUBLICADO` o `EN_PROCESO`), **no por las fechas**. El cargo ocupado se excluye del desplegable del alta y del alta con cargo fijado (que se oculta en Cargo → Ver); el **Servidor** aplica la misma regla en el alta y en todo cambio de cargo (`ConcursoService.preAlta`/`preModificacion`), así que una petición directa al REST también se rechaza ("El cargo ya tiene un concurso abierto: ciérrelo antes de abrir otro."). En la **edición** se valida solo cuando cambia el cargo: conservar el cargo actual y **cerrar** (`FINALIZADO`) no se bloquean, para poder corregir o cerrar registros históricos duplicados. Los concursos finalizados o eliminados no cuentan como abiertos.

**3.2 Postulación / toma de horas** (concurso abierto)
- Docente obligatorio y **activo**.
- Rechaza si el docente **ya tiene toma de horas vigente en este concurso**.
- **Exclusión por reemplazo propio** (regla general): no puede postularse quien tiene una **designación ACTIVA vigente en el mismo cargo** (no se selecciona a sí mismo como reemplazo).
- **Exclusión en rellamados**: no puede volver a seleccionarse quien **ya obtuvo designación efectiva en el concurso origen** para ese cargo.
- Horas: **mínimo 1** y **máximo las horas disponibles** del concurso.
- Al registrar: `ganador=false`, fecha de emisión = hoy, estado inicial `PENDIENTE_CERTIFICADOS`.
- **Doble barrera**: la exclusión se aplica tanto en el selector (GET) como en la validación del POST.
- **Validación en el Servidor**: todas las reglas de esta sección se validan también en `ConcursoDocenteService`, así que una petición directa a `POST /concursos-docentes` las respeta. El Servidor fija además los valores iniciales (`ganador=false`, fecha de emisión = hoy, estado `PENDIENTE_CERTIFICADOS`), sin importar lo que se envíe.

**3.3 Modificación de horas**
- Solo con el concurso abierto; solo para postulaciones **pendientes** (certificados o posesión); horas ≥ 1 y ≤ máximo disponible.
- **Ciclo de estados validado en el Servidor** (`PUT /concursos-docentes/{id}`): solo se permiten las transiciones `PENDIENTE_CERTIFICADOS` → `DESIGNADO_PENDIENTE_POSESION` | `CANCELADO_INEFECTIVO` y `DESIGNADO_PENDIENTE_POSESION` → `DESIGNACION_DEFINITIVA` | `CANCELADO_INEFECTIVO`, siempre con el concurso abierto; `DESIGNACION_DEFINITIVA` y `CANCELADO_INEFECTIVO` son finales. Tampoco se puede cambiar el concurso ni el docente de una postulación. Los registros históricos sin estado (§9) no se validan, para que sigan siendo editables.

**3.4 Validación de certificados** (concurso abierto, postulación `PENDIENTE_CERTIFICADOS`)
- Decisión obligatoria: Aprobar o Rechazar.
- **Aprobar** requiere marcar certificados recibidos; si exige vencimiento, meses ≥ 1 (si falta, se re-renderiza el formulario conservando lo cargado para corregir). Resultado → `DESIGNADO_PENDIENTE_POSESION`.
- **Rechazar** → `CANCELADO_INEFECTIVO` y **restitución de las horas** reservadas al concurso.

**3.5 Toma de posesión** (postulación `DESIGNADO_PENDIENTE_POSESION`)
- Fechas de inicio efectiva y fin definitiva obligatorias (defaults: hoy y +1 año).
- El **concurso debe tener carácter definido** (si no, error pidiendo completarlo).
- La designación **hereda el carácter del concurso**, su **motivo de la vacancia**, su **profesor reemplazante** y el **origen de la vacancia** capturado en él (los concursos anteriores al campo usan el respaldo `"Concurso: <descripción>"`); estado = **ACTIVO**; la fecha de fin cargada en el formulario se persiste directamente como `fechaFinDefinitiva`; guarda además el **vínculo directo** con el concurso (`concursoId`).
- **Validación de vigencia**: se rechaza si el cargo ya tiene una designación ACTIVA/PENDIENTE del mismo docente ("El docente ya posee una designación vigente para este cargo."). Se valida también en el Servidor (ver §4).
- La designación se crea y se agrega al cargo; la postulación queda `ganador=true` → `DESIGNACION_DEFINITIVA`.
- **Una sola operación en el Servidor** (`POST /concursos-docentes/{id}/posesion` → `ConcursoDocenteService.confirmarPosesion`, transaccional): valida (concurso abierto, postulación pendiente de posesión, carácter definido, fechas), crea la designación con las reglas de §4, la vincula al cargo, avanza la postulación y cierra el concurso si quedó completo. El Cliente solo envía fechas y muestra el resultado: una llamada directa a la API no puede dejar la designación creada sin la postulación actualizada (ni al revés).

**3.6 Declarar inefectivo** (solo `DESIGNADO_PENDIENTE_POSESION`)
- → `CANCELADO_INEFECTIVO` con observación (default: "No se presentó a la toma de posesión") y **liberación de horas**.

**3.7 Cierre del concurso**
- Manual: prohibido cerrar si hay docentes **pendientes de validación de certificados**; al cerrar con horas libres avisa que puede abrirse un rellamado. La regla vive en el Servidor (`ConcursoService.preModificacion`): toda escritura que deje el concurso en `FINALIZADO` desde un estado abierto pasa por ahí, así que también rechaza una llamada directa a la API (no es un cierre nuevo el que ya venía cerrado).
- **Automático**: al confirmar una posesión, si **todas las postulaciones vigentes quedaron con designación definitiva y no quedan horas disponibles**, el concurso pasa solo a FINALIZADO (`ReglasConcurso.esListoParaCierre`, evaluado en el Servidor dentro de la misma operación de §3.5).

**3.8 Rellamado** (solo desde concurso **cerrado** con horas remanentes > 0)
- Crea un nuevo concurso PUBLICADO que: hereda **cargo, carácter, motivo/origen de la vacancia y profesor reemplazante del origen** (sin selector), recibe las **horas remanentes** como totales, marca `esRellamado=true` y `concursoOrigenId`. Si el origen solo tiene el texto heredado, el motivo se deduce con `MotivoVacancia.desdeTexto`.
- Las horas remanentes se **transferen**: se persisten como `horasTransferidas` en el origen y este queda con 0 horas disponibles, incluso tras reiniciar el Cliente.
- Descripción por defecto: "Rellamado – <origen>".
- **Todo el rellamado se resuelve en el Servidor** (`ConcursoService.alta` cuando llega `concursoOrigenId`, transaccional): verifica origen existente y cerrado, calcula las horas remanentes, hereda cargo/carácter/motivo/reemplazante/horas y asienta la transferencia en el origen. El Cliente solo envía fecha y descripción; las reglas no dependen de que el formulario las repita.

**3.9 Flujos legacy eliminados**
- **Agregar cargo** y **Ganador** se retiraron del Cliente (vistas y endpoints): eran pantallas **sin entrada en la interfaz** que creaban designaciones al margen de la postulación. La única vía de creación desde un concurso es la toma de posesión (§3.5), que además es la que marca al ganador (`ganador = true`).
- Consecuencia: **ningún flujo vigente genera designaciones en estado `PENDIENTE`** (ver §4 y §9).

**3.10 Cálculo de horas** (reglas de `FlujoConcursoService`)
- Totales: DTO persistido → carga horaria del cargo (respaldo).
- Asignadas: suma de horas de postulaciones **vigentes** (excluye canceladas y sin horas).
- Disponibles: **total − asignadas − transferidas a un rellamado**, nunca menor a 0.
- Listo para cierre: todas las vigentes definitivas y disponibles ≤ 0 (misma regla en el Cliente y en el Servidor: `FlujoConcursoService` / `ReglasConcurso.esListoParaCierre`).

## 4. Reglas de Designaciones

- **Múltiples designaciones históricas permitidas** para el mismo docente y cargo (no es duplicado).
- **Única prohibición**: dos designaciones **vigentes simultáneas** (ACTIVO/PENDIENTE) para el mismo docente y cargo. Se valida por **lógica de negocio**, nunca con UNIQUE(docente_id, cargo_id) (bloquearía las históricas). El Servidor (`DesignacionService`) la valida en el alta de **toda designación creada desde un concurso** (toma de posesión), resolviendo el cargo por el `concursoId`; las altas manuales no tienen cargo vinculado y no se validan.
- **La vigencia se determina por estado, NO por fechas**: las demás reglas siguen consultando `estadoDesignacion` y nunca `fechaFinDefinitiva`; lo que cambió es que **el estado se deriva de esa fecha**. Al dar de alta o modificar una designación (manual o creada desde el concurso) y también en la **revisión diaria** (§10), el Servidor calcula: sin fecha de fin o con fecha **futura** → `ACTIVO`; con la fecha de fin **alcanzada** (hoy o anterior) → `FINALIZADO`. La `PENDIENTE` es un **estado residual**: se conserva si ya está persistida, pero **ningún flujo vigente la genera** (la espera por la toma de posesión vive en la postulación, §3.5).
- El formulario de designación (alta y edición) **no permite elegir el estado**: lo muestra como dato calculado (en el alta, «Se calcula al guardar»); la regla vive en el Servidor (`DesignacionService.validar`), que además **rechaza** una fecha de fin definitiva **anterior** a la fecha de inicio efectiva. En la edición de una designación `PENDIENTE` **histórica** se ofrece el checkbox **«Confirmar toma de posesión»**, la única vía para pasarla a `ACTIVO` (después el estado se rige por la fecha de fin).
- Al modificar una designación (formulario manual) se **conservan sus alertas y licencias** asociadas.
- **Navegación con contexto**: editada desde Cargo → Ver, tanto **Cancelar** como el **guardado exitoso** vuelven al detalle de ese cargo (mecanismo `cargoId`); desde otros accesos, el comportamiento es el original (lista general).
- Cada designación creada desde un concurso guarda su **vínculo directo** con él (`concursoId`), el **motivo**, el **profesor reemplazante** y el origen de la vacancia heredados (snapshot); las designaciones manuales e históricas (sin vínculo) se resuelven por el texto `origenVacancia`.
- El **profesor reemplazante** se carga en el formulario de la designación (alta y edición) como texto opcional y se muestra: en el detalle de la designación y, junto al motivo, en la tabla **Designaciones registradas** de Docente → Ver («Reemplaza a: …» cuando tiene valor).
- Sin restricción UNIQUE en BD: se reemplazó por índice no único (`idx_designacion_docente`) para permitir el historial.
- **Alta manual** (endpoint `/designaciones/nuevo`, **sin entrada en la interfaz**): el formulario **no tiene selector de cargo** y la designación se persiste con `cargo_id = NULL`; el `cargoId` del formulario es solo contexto de navegación (volver al cargo). Usa el **mismo selector de motivo** que los concursos (detalle opcional y obligatorio en «Otro») y guarda el texto compuesto en `origenVacancia`, que sigue siendo NOT NULL. Consecuencias de la falta de cargo: no aparece en Cargo → Ver, **no suma en "Designaciones activas" ni en Vacante/Ocupado** de ningún cargo, y el detalle del docente muestra "—" en Materia/Curso. El módulo Designaciones queda como **consulta/edición** de las designaciones creadas por concurso.
- Las designaciones creadas por el **flujo de concursos** (toma de posesión) sí quedan **vinculadas al cargo** (se agregan a su colección) y son las que cuentan en el cargo.

## 5. Reglas de Cargos

- **Carga Horaria en Cargo → Ver** = **horas que el docente tomó en el concurso** (no la carga total del cargo). Se resuelve por el **vínculo directo** `concursoId` → postulación del mismo docente → horas efectivas; las designaciones históricas (sin vínculo) conservan el respaldo `origenVacancia = "Concurso: <desc>"`; si no resuelve, **fallback** a la carga del cargo.
- Un cargo agrupa sus designaciones; desde ahí se accede a editar cada una (conservando contexto).
- La designación vigente de un cargo es la base de la **exclusión de docentes** en concursos de ese cargo.
- **Listado de cargos — columna "Designaciones activas"**: cuenta las designaciones **no eliminadas** con estado **ACTIVO** de cada cargo (mismo criterio que la tarjeta "Designaciones activas" del detalle del cargo). No se cuentan las `FINALIZADO`, las `PENDIENTE` ni las eliminadas; destacar que **"activa" (solo `ACTIVO`) no es lo mismo que "vigente" (ACTIVO o PENDIENTE)**. El cálculo se resuelve en la **fuente de datos** (`CargoController` del Cliente), no en la vista.
- **Estado del cargo (derivado)**: el listado de cargos **ya no muestra la columna "Estado"** (cambio solo visual). El cálculo de Vacante/Ocupado se mantiene intacto y se sigue mostrando como badge en el detalle del cargo: **Vacante** = sin designación ACTIVO/PENDIENTE; **Ocupado** = con al menos una vigente.
- El botón "Nueva designación" está **oculto en todo el sistema** (Cargo → Ver, listado de Designaciones y su estado vacío): las designaciones se crean solo por el flujo del concurso. La vista de alta y su endpoint se conservan sin enlaces, y el módulo Designaciones queda como **consulta/edición**.
- **Cargo → Ver — tabla "Designaciones del cargo"**: incluye las columnas **Motivo** y **Profesor reemplazado** (ambas con "—" cuando falta el dato; no se inventa información). El motivo es el texto de la vacancia heredado del concurso o cargado a mano; las designaciones históricas que solo tienen el respaldo `"Concurso: <descripción>"` muestran "—" para no presentarlo como motivo. El profesor reemplazado es el guardado en la designación (heredado del concurso o manual). Se resuelven en la fuente de datos (`CargoController`), reutilizando el DTO de la designación sin duplicar datos.
- **"Abrir concurso para este cargo"** se reemplaza por el badge **"Concurso abierto"** cuando el cargo ya tiene un concurso abierto (§3.1), para no ofrecer una acción que el backend rechaza.

## 6. Reglas de Docentes

1. **Regla general** (todo concurso): excluir docentes con designación **ACTIVA** vigente en el mismo cargo del concurso (selector + POST).
2. **Regla de rellamados** (acumulativa): excluir además a quienes obtuvieron **designación efectiva en el concurso origen**, reconocidas por su **vínculo directo** (`concursoId`) con el texto `"Concurso: <desc>"` como respaldo histórico (resuelto desde el DTO persistido; *fail-open* si no se puede determinar el origen).
- No se excluye por designaciones de otros cargos ni a docentes sin designación efectiva.
- En el selector también quedan fuera docentes con toma de horas vigente en el mismo concurso.
- **Historial de concursos del docente** (detalle): solo se listan los concursos con **designación definitiva** concretada (cualquier estado del concurso) o con **postulación vigente** en concurso `PUBLICADO`/`EN_PROCESO` (etapa en la que todavía puede concretarse la designación). Se excluyen los concursos finalizados o cancelados sin designación definitiva. El filtro se aplica en la **fuente de datos** (`DocenteController`), no en la vista.

## 7. Reglas de eliminación de entidades

Toda baja es **lógica** (`eliminado = true`): ningún registro se borra físicamente, así que las claves foráneas no se rompen. Lo que se protege es el **huérfano lógico**: que un dato desaparezca y deje a otro sin contexto. La regla se valida en el **Servidor, dentro del servicio** (hook `preBaja` de `BaseService`), no en el controlador: una petición directa al endpoint DELETE también se rechaza (HTTP 400 con el mensaje de la regla, sin «Error de Sistemas») y el registro rechazado no se modifica ni pierde sus relaciones. El botón de eliminar permanece visible; el Cliente muestra el mensaje del Servidor como alerta en la lista, sin mensaje de éxito.

| Entidad | Se rechaza la baja si… |
| --- | --- |
| **Concurso** | tiene **postulaciones** no eliminadas que no estén declaradas inefectivas (`CANCELADO_INEFECTIVO`), o tiene **cualquier designación** vinculada (activa, pendiente, finalizada o histórica sin estado). No depende del estado del concurso. |
| **Designación** | su estado **no es `PENDIENTE`**: las `ACTIVO` se finalizan cargándoles la fecha de fin y las `FINALIZADO` (y las históricas sin estado) son historial. |
| **Cargo** | tiene **concursos** no eliminados, o **designaciones** en cualquier estado (no eliminadas). |
| **Docente** | tiene **cualquier designación** asociada (no eliminada) en cualquier concurso, **sin importar su estado**; o está **asociado a un concurso** con alguna postulación no eliminada ni declarada inefectiva (`CANCELADO_INEFECTIVO`), **haya ganado o no**. |
| **Tipo de certificado** | hay **certificados** cargados (no eliminados) de ese tipo en cualquier concurso. |
| **Curso** | está asociado a una **materia** (no eliminada) o a un **cargo** (a través de la materia del cargo). |
| **Materia** | está asociada a un **cargo**. |
| **Postulación (toma de horas)** | siempre: no se eliminan, se **cancelan declarándolas inefectivas** (conserva el historial y libera las horas). |
| **Certificado** | siempre: no se eliminan, se **reemplazan cargando uno nuevo** del mismo tipo (el anterior queda en el historial, §10). |

Mensajes (el Cliente los muestra tal cual):

- «No se puede eliminar el concurso porque tiene postulaciones asociadas.» / «…porque tiene designaciones asociadas.»
- «No se puede eliminar una designación activa: cárguele la fecha de fin definitiva para finalizarla.» / «No se puede eliminar una designación finalizada: forma parte del historial.» / «No se puede eliminar una designación histórica: forma parte del historial.»
- «No se puede eliminar el cargo porque tiene concursos asociados.» / «…porque tiene designaciones asociadas.»
- «No se puede eliminar el docente porque tiene designaciones asociadas.» / «…porque tiene postulaciones en concursos.»
- «No se puede eliminar el tipo de certificado porque hay certificados cargados de este tipo en concursos.»
- «No se puede eliminar el curso porque está asociado a una materia.» / «…a un cargo.» · «No se puede eliminar la materia porque está asociada a un cargo.»
- «Las postulaciones no se eliminan: se cancelan declarándolas inefectivas.» · «Los certificados no se eliminan: para reemplazarlo, cargue uno nuevo (el anterior queda en el historial).»

Consecuencias prácticas:

- Un concurso con **todas** sus postulaciones declaradas inefectivas y **sin** designaciones vinculadas **sí** se puede eliminar (limpieza de concursos que quedaron sin docentes).
- Un concurso `FINALIZADO` cuya única designación quedó `FINALIZADO` **no** se puede eliminar: el historial de la designación apunta al concurso (`concursoId`). Tampoco se puede eliminar antes esa designación, porque es historial.
- El **cargo** queda bloqueado mientras exista cualquier concurso o designación que lo referencie, incluso finalizados.
- El **docente** queda bloqueado por cualquier designación (incluso finalizada) y por cualquier postulación en un concurso, **también las ganadoras**. Solo se libera si sus únicas postulaciones fueron **declaradas inefectivas**; el docente inactivo sigue el mismo criterio (la baja lógica no es «dar de baja» al docente: para eso se usa Activo/Inactivo).
- La asociación del **docente** con el concurso se mide sobre el estado persistido de la postulación: las declaradas inefectivas (`CANCELADO_INEFECTIVO`) no bloquean, y los registros antiguos **sin estado** se tratan de forma conservadora (bloquean, ver §9).

## 8. Reglas transversales de UI / integridad

- **Motivo de la vacancia**: los formularios de concurso y de designación usan el **mismo desplegable** (`MotivoVacancia`) con un campo de **detalle** siempre visible. `app.js` (§7) alterna la obligatoriedad del detalle con `data-required-when="#motivoVacancia" data-required-value="OTRO"`: sin JavaScript el alta exige el texto por validación del Servidor.
- **Fecha de fin no anterior al inicio**: los formularios que cargan ambas fechas (toma de posesión, y alta y edición de designación) usan `data-min-from="#fechaInicioEfectiva"` (`app.js` §8), que fija el `min` del campo de fin en el navegador. Es solo anticipación visual: la regla sigue viviendo en el Servidor (`DesignacionService.validar`), que rechaza una fecha de fin definitiva anterior a la de inicio.
- **Listado de concursos — filtros y orden**: búsqueda en vivo, filtro por **estado** con botones (Todos / Publicados / En proceso / Finalizados), **rango de fechas desde/hasta** sobre la fecha que se muestra en el listado (fecha del concurso o, si no está cargada, la de cierre) y **orden** por **fecha de apertura** (más recientes / más antiguos, con el id como desempate). Se resuelven en el cliente con `app.js`, que **combina** texto, estado y fechas sobre la misma tabla (los criterios ya no se pisan), muestra el estado vacío "Sin resultados" y el botón **Limpiar** restablece los tres. Las fechas de filtro y las de orden se envían como atributos de fila calculados en la fuente de datos (`ConcursoController` del Cliente). La vista los presenta en un **panel de filtros** (`filter-panel`) con una etiqueta por control (Buscar, Fecha desde, Fecha hasta, Ordenar por, Estado) y deja el alta **Nuevo concurso** en la cabecera, separada de los filtros.
- Sin selector manual de estado de concurso; sin selector de carácter en rellamados (herencia no editable).
- Eliminación de botones redundantes: se quitó "Ver" en la tabla de designaciones del cargo y "Ver detalle" en la edición de designación (las vistas/endpoints de detalle se conservan para otros accesos).
- `estado_concurso` alineado a nivel BD: ENUM('PUBLICADO','EN_PROCESO','FINALIZADO') + CHECK `concurso_estado_concurso_valido` (los ALTER no los hace `ddl-auto`).
- Atributos retirados del modelo y de la BD: `Docente.puntaje`, `Designacion.fechaFinEstimada` y `Curso.seccion` (con sus referencias en DTOs, formularios, mapeos y vistas). Las columnas físicas `docente.puntaje`, `designacion.fecha_fin_estimada` y `curso.seccion` se retiraron con ALTER explícito, ya que `ddl-auto` no elimina columnas. El detalle de designación muestra solo **fecha de inicio efectiva** y **fecha de fin definitiva**.
- Detalle del docente (vista): secciones en orden **Designaciones registradas → Historial de concursos**; la tabla de designaciones muestra **Materia/Curso**, **Motivo y profesor reemplazado** (texto del origen de la vacancia y, debajo, «Reemplaza a: …» si hay dato), **Carácter**, **Inicio efectivo**, **Fecha fin definitiva** (vacía si no existe) y **Estado**; no tiene botón "Ver" (esas designaciones se consultan vía Cargo → Ver).
- Detalle del docente — acciones: sin botones finales "Agregar certificado" y "Editar docente" (las vistas y endpoints se conservan para otros accesos).
- Formularios de docente: el alta **no tiene sección "Opciones"** y crea al docente **activo** por defecto (campo oculto); en la edición **Activo** e **Interno** no son editables — al guardar se **preservan sus valores actuales** (campos ocultos). El listado muestra "Interno" junto al nombre y la columna Estado (Activo/Inactivo).
- Los datos del flujo (**estado de postulación, certificados/observación, horas totales y transferidas, `esRellamado`/`concursoOrigenId`**) se persisten en el Servidor; el store temporal del Cliente (`PostulacionDatosStore`) fue **eliminado** y todas las lecturas/escrituras pasan por el DTO.

## 9. Limitaciones / deuda conocida (reglas al momento actual)

- **`PENDIENTE` es un estado residual**: ningún flujo vigente lo genera (solo lo producía «Agregar cargo», eliminado en §3.9). Las filas históricas con ese estado se conservan, siguen contando como **vigentes** (§4/§5) y se regularizan con el checkbox «Confirmar toma de posesión». No se elimina del enum porque hay datos persistidos con ese valor y varias reglas lo consultan.
- Las vistas `concursos/ganador.html` y `concursos/agregar-cargo.html`, con sus endpoints, se **eliminaron** por no tener entrada en la interfaz (§3.9); la postulación sigue marcando al ganador al confirmar la toma de posesión.
- Designaciones **históricas** sin horas de concurso resueltas muestran el fallback (carga del cargo).
- La exclusión de rellamado es **fail-open** si no logra resolver el origen.
- Los registros antiguos **sin estado de postulación** derivan su estado del ganador (`ganador ? DESIGNACION_DEFINITIVA : PENDIENTE_CERTIFICADOS`) y en las validaciones de eliminación se tratan de forma conservadora (bloquean la baja).
- Las transferencias de horas hechas **antes** de esta migración que solo vivían en memoria no son recuperables (mismo comportamiento que un reinicio previo del Cliente); los concursos viejos sin `horasTotales` siguen resolviendo sus horas totales por la carga del cargo.
- Los concursos y designaciones **anteriores** al selector tienen `motivoVacancia = NULL`; el formulario lo deduce del texto guardado (`desdeTexto`) y, si el texto no coincide con ninguna etiqueta, lo muestra como «Otro» con el texto completo para no perder información. Los registros guardados conservan su `origenVacancia` original hasta que se editen; al editarlos, el campo de detalle muestra el texto sin el motivo y al guardar se recompone en el formato `Motivo – detalle`.
- El **profesor reemplazante** se guarda en el concurso y en la designación (heredado o manual) y se muestra en el detalle de la designación y en Docente → Ver; no se asocia al docente ni se incluye en el PDF de designaciones (sí en el del concurso). Las designaciones anteriores a este cambio quedan sin el dato y se completa editándolas.
- La regla de **un solo concurso abierto por cargo** (§3.1) no corrige los duplicados **históricos** ya existentes: se muestran normalmente y se pueden editar o cerrar; lo que no se permite es abrir uno nuevo sobre ese cargo hasta cerrar el anterior. La exclusión se calcula en memoria en los controladores del Cliente (con *fail-open* si el Servidor no responde); la garantía definitiva es la validación del Servidor.
- El **rango de fechas** del listado usa la fecha que se muestra (toma de horas o cierre) y el **orden** la fecha de apertura: son criterios distintos y se documentan así para no confundirlos.
- Las **alertas de concurso** (§10) necesitan `fechaCierre` y un estado `EN_PROCESO` o `PUBLICADO` con cierre vencido; un concurso `PUBLICADO` sin fecha de cierre no se alerta. La alerta de **horas sin cubrir** necesita `horasTotales` o, en su defecto, la carga horaria del cargo: sin ninguno de los dos no se puede calcular el remanente y no se alerta.
- Una alerta automática **leída no se reabre mientras la prioridad no cambie**: si la causa se resuelve y luego reaparece con la misma gravedad, queda leída (no se borra). Tampoco se reabre por el paso de los días, aunque el mensaje diga «hace N días» o «faltan N días».
- Las alertas automáticas creadas antes de este esquema podían tener **dos registros por causa** (MEDIA y ALTA). Al evaluarse la causa se consolidan en una sola: se conserva la de mayor gravedad y la otra se marca leída.
- **Transición `PUBLICADO → EN_PROCESO` (deuda)**: la persiste el Cliente desde un GET (`ConcursoController.aplicarTransicionEstado`, al abrir el listado o el detalle) y, si la escritura falla, se ignora el error. Las alertas ya lo compensan (calculan el estado efectivo), pero el PDF de reportes imprime el estado **persistido** y un GET que escribe no es idempotente para una API. Criterio propuesto: mover la transición al Servidor —derivarla al leer o persistirla en la tarea diaria del `AlertasScheduler`— y dejar el GET del Cliente sin escrituras. Mientras tanto, los cambios de estado que sí son de negocio (cierre, cierre automático) ya están en el Servidor (§3.7).
- **Validaciones de alta de concurso**: resueltas en el Servidor (`ConcursoService.alta`/`preAlta`): descripción, cargo, fecha y carácter obligatorios, `fechaApertura` por defecto, horas totales con la carga del cargo como respaldo **persistidas** y `PUBLICADO` como estado inicial (el carácter se hereda en los rellamados, así que no se exige en ese formulario). El **motivo de la vacancia** queda como validación del Cliente, que envía el texto ya compuesto. Los concursos viejos sin `horasTotales` siguen resolviendo sus horas por la carga del cargo (§3.10), porque no se reescriben al leerlos.

## 10. Alertas automáticas

Todas usan la entidad `Alerta` (mensaje, fecha de emisión, prioridad, leída, baja lógica) y se muestran con el mismo formato en Alertas y en el dashboard. El `AlertasScheduler` las revisa todos los días a las 6:00 (zona Argentina) y también al iniciar el Servidor. Antes de depurar y generar, **finaliza las designaciones ACTIVO cuya fecha de fin definitiva ya se alcanzó** (§4): así el vencimiento por el solo paso de los días actualiza el estado y no deja su alerta pendiente.

- **Certificados provisorios por vencer**: 3 días o menos → ALTA; 7 días o menos → MEDIA. No alerta certificados reemplazados, definitivos ni de postulaciones canceladas. Al cargar la renovación, el certificado anterior pasa al historial y **su alerta se resuelve en el acto** (queda leída con fecha de resolución), sin esperar la corrida diaria.
- **Fin de designación**: solo designaciones **ACTIVO** con fecha de fin definitiva. 0 a 10 días → ALTA (incluye las ya vencidas que siguen ACTIVO); 11 a 15 → MEDIA. Se evalúa al crear y al modificar la designación, así que corregir la fecha se refleja en el acto. Si la fecha se alcanza **sin que nadie edite** la designación, la corrida diaria la pasa a `FINALIZADO` y su alerta queda leída.
- **Concurso en proceso sin cerrar**: se cuentan los días **transcurridos desde `fechaCierre`**. 3 días o más → MEDIA; 7 o más → ALTA. Se consideran los `EN_PROCESO` y también los `PUBLICADO` con cierre vencido (estado efectivo), porque la transición la persiste el Cliente al abrir el listado. El vínculo se guarda en `alerta.concurso_id` (id plano) y `alerta.tipo` distingue las dos alertas de concurso.
- **Concurso cerrado con horas sin cubrir**: si el concurso queda `FINALIZADO` con horas que no tienen designación ni se transfirieron a un rellamado, se crea una alerta **MEDIA** («El concurso … quedó cerrado con N horas sin cubrir»). Se resuelve sola al transferir las horas al rellamado, al cubrirlas, o si el concurso se elimina o se reabre. Un mismo concurso puede tener esta alerta y la de «sigue en proceso», porque se distinguen por `tipo` (`CONCURSO_SIN_CERRAR` / `CONCURSO_HORAS_SIN_CUBRIR`).
- **Una alerta por causa**: cada certificado, designación o concurso tiene una sola alerta automática. Cuando cambia el umbral se actualizan la prioridad y el mensaje, no se crea otra: al escalar (MEDIA → ALTA) la alerta vuelve a pendiente aunque estuviera leída, y si estaba dada de baja se reactiva; al bajar (ALTA → MEDIA) se actualiza sin reabrir. La baja manual se respeta mientras no haya escalado.
- **Auto-resolución (no se borra nada)**: en cada corrida primero se **finalizan las designaciones vencidas** (§4) y se marcan **leídas** las alertas automáticas cuya causa desapareció (certificado reemplazado, designación finalizada o eliminada, concurso cerrado o eliminado, horas que se cubren o se transfieren) y después se generan las que faltan. Los cambios de prioridad no resuelven la alerta: la actualizan. Las manuales y las dadas de baja nunca se tocan.
- **Fecha de resolución**: `alerta.fecha_resolucion` la registra el Servidor cuando la alerta pasa a leída (manual o automática), la conserva al editar una alerta resuelta y la limpia si vuelve a pendiente. El detalle muestra «Esta alerta fue resuelta el DD/MM/AAAA»; las leídas anteriores a esta columna no tienen fecha y muestran el aviso sin fecha.
- **Resolución inmediata**: las alertas de certificado se reevalúan al cargar el certificado (el nuevo, por si ya vence dentro del umbral) y al renovarlo (el anterior queda en el historial y su alerta se resuelve en el mismo alta); las de designación se reevalúan al crear o modificar la designación, y las de concurso (ambas) al modificar el concurso, lo que incluye el **cierre manual y el automático** (este último al completar todas las horas con designación definitiva) y la **transferencia de horas al rellamado**. Al **dar de baja** la causa (designación `PENDIENTE` residual o concurso sin actividad, §7) o **reemplazar** el certificado (que no se elimina), sus alertas también se resuelven en el acto: se **marcan leídas, no se eliminan**, para conservar el historial y la fecha de resolución (eliminarlas sigue siendo una acción manual desde el listado de Alertas). Así la causa resuelta marca la alerta como leída en el acto y no hay que esperar a la corrida diaria. La depuración de la corrida sigue cubriendo los datos anteriores a estos cambios.
- La primera corrida genera las alertas de todo lo que ya está en rango; a partir de ahí se mantienen solas.
