# Roadmap — Rellenador de Contratos PdV (Android)

Estado real del proyecto y próximas tandas planificadas. Este documento sustituye al
"roadmap" informal que vivía en las notas de continuidad de las sesiones. Se actualiza
al final de cada tanda con lo que quede pendiente.

Última actualización: **2026-09-02** (versión `0.10.9-fase5-clave-real`, versionCode 79, verde).

> **El roadmap visual, con el estado real de las 7 fases, está en
> [`docs/roadmap-multiformulario.html`](docs/roadmap-multiformulario.html)** — se actualizó el
> 2026-09-02 contra el código de `main`. Ahí está qué se implementó de cada fase, qué falta y por
> qué. Resumen: fases 1–3 hechas, fase 4 implementada pero con su objetivo a medias (el editor vive
> en Ajustes y no sustituye el mapeo del asistente), fase 5 en curso (faltan 5·4 y 5·5), fases 6 y 7
> sin empezar y reasignadas a `0.11.0` y `1.0.0`.

> **Cambio de contexto (2026-08-31):** Pablo ya no trabaja con Orange/MASORANGE. La
> prioridad pasa a ser multi-contrato de verdad, con los PDFs de la empresa nueva
> (Aire Networks) como caso real — no solo el Modelo 145 de prueba. El contrato Orange
> se mantiene como esquema `BUILTIN` reconocible (fase 2), pero deja de ser el centro
> de la app. El orden de fases no cambia, pero la prioridad de llegar hasta la fase 5
> (Relleno dinámico) sí sube: sin ella, subir un PDF de Aire sigue sin poder rellenarse
> de principio a fin.

### Decisión de arquitectura (2026-08-31): modelo de expediente desde la fase 2

Un alta en Aire **no es un formulario, es un conjunto**. La propia documentación de Aire
(«Documentación para clientes nuevos») lo fija: CIF + SEPA firmado + CONTRATO firmado + DNI
del representante + móvil/email + cuenta bancaria, más los anexos que apliquen (portabilidad,
conectividad). Los mismos datos de cliente se repiten en los cuatro PDFs con nombres de campo
distintos (`Nombre o razón social` / `NOMBRE DEL DEUDOR` / `Titular`).

**Decidido: la unidad persistida se modela como expediente desde la fase 2, pero la interfaz
sigue trabajando sobre un formulario cada vez.** Es decir, la fase 2 define ya un contenedor
con (a) una lista de formularios —que arranca con exactamente un elemento— y (b) encima, los
datos de cliente compartidos a los que cada esquema mapea sus campos vía `canonical`.

Motivo: las dos alternativas obvias salen peor.
- *Un formulario ahora y convertir a expediente después* → se pagan **dos migraciones** de
  datos persistidos (`Map<canónica,real>` → `FormSchema` → `Expediente`), cada una sobre
  datos de trabajo real.
- *Ir directo a expediente completo* → se toca a la vez el modelo **y** todo el asistente
  (`WizardState` asume hoy un contrato: un `contractSource`, un juego de firmas, un juego de
  valores). Es el riesgo de la fase 5 multiplicado, sobre lo que se usa a diario.

Con esta decisión no hay cambio visible ni migración futura: cuando llegue la fase de
expediente, sólo hay que permitir añadir elementos a una lista que ya existe y ya se guarda.

**Consecuencias para el modelo de la fase 2** (detectadas analizando los 4 PDFs de Aire):
- `canonical` deja de ser «enlace al CANON del contrato Orange» y pasa a ser **el canónico
  transversal del expediente** (razón social, CIF/NIF/NIE, domicilio, CP, localidad,
  provincia, representante, IBAN…). Es lo que hace que extraer una vez sirva para los cuatro.
- Hace falta un **origen `PLATAFORMA`** para campos que no salen de ninguna documentación
  (`FECHA DE ALTA EN TEKI`, `CÓDIGO DE CLIENTE EN TEKI`): ni se autorrellenan ni deben
  bloquear el avance como si fueran un conflicto por decidir.
- El **bloque DISTRIBUIDOR es constante** (nombre, teléfono, email y código de distribuidor,
  más nombre y DNI del comercial al pie de los cuatro formularios). Sucesor directo del
  autorrelleno de `RESPONSABLE_KEY`: se configura una vez en Ajustes y aplica a todo.
- Los **valores de exportación de checkbox son arbitrarios** (`/Sí` en Portabilidad,
  `/Opción1` en el SEPA). Hay que leerlos de `/AP /N`, no asumir `/On`–`/Off` como hace hoy
  `checkboxStateFor()`. Esto se rompe con los PDFs de Aire si no se corrige en la fase 2.
- Las **tablas se detectan por geometría** (x constante = columna, y constante = fila), nunca
  por el nombre: en una misma fila conviven `TF cantidad 01` y `Campo de texto 116`. Los
  checkboxes de fila se asocian igual: en Portabilidad el prefijo (`Check Box4..7`) da la
  columna y la posición da la fila, pese a llamarse `Check Box4.4.5.10.5`.
- Un **valor lógico puede estar troceado en varias casillas** (el BIC del SEPA son 11 campos
  de un carácter).

### La fase 2 se ejecuta en tres tandas (2026-08-31)

Tal como estaba descrita, la fase 2 metía en una sola versión tres cosas con riesgos muy
distintos, y la más arriesgada (migrar datos ya persistidos) es justo la que no debe compartir
tanda con nada. Partida así:

| Tanda | Versión | Alcance | Riesgo |
|---|---|---|---|
| 1 de 3 | `0.9.7` ✅ | Estado real de las casillas (`/AP /N`). Autónoma, no depende del modelo. Arregla algo **roto hoy** con los PDFs de Aire. | Bajo |
| 2 de 3 | `0.9.8` ✅ | El modelo: `FormSchema` / `FormSection` / `FormField` / `ValueOrigin`, y el esquema `BUILTIN` derivado de `CANON`. Estructuras nuevas, nada que romper. | Bajo |
| 3 de 3 | `0.9.9` ✅ | Persistencia y migración: convertir los `Map<canónica,real>` guardados al formato nuevo con `schemaVersion`, y el contenedor de expediente (lista de 1). | **Alto** |

La tanda 3 toca datos de trabajo reales y es la que ya dolió en la 0.8.0 con el índice de
paso. Build verde y verificación en el móvil antes de seguir.

---

## Completado (versiones con build verde)

| Versión | Contenido |
|---|---|
| **0** | Fundación: paleta de color desde el naranja de marca, `MotionScheme.expressive()`, terciario azul-verdoso. |
| **1** | Wizard shell: TopAppBar con `primaryContainer`, stepper de 3 estados, `LoadingIndicator` Expressive. |
| **Mockup Contrato + Ajustes** | Formas "blob", `ContractOptionCard`, `TipBanner`, Ajustes como `ModalBottomSheet`. |
| **2** | Documentación: chips de motor con logo real (`ProviderLogo`/`ProviderGlyph`), `MotorLoadingIndicator` contextual, callbacks `onProviderStart/Finish` en el extractor. |
| **3** | Revisión IA + Relleno: secciones con `surfaceContainer`, tipo de identificación NIF/CIF/NIE editable, progreso real, "copiar fiscal", fecha compacta, chips de candidato con logo, formas unificadas. |
| **0.5.6** | `Actividad principal del negocio` + `Profesión...` en `CANON`, checkbox NIE (`undefined`) arreglado, prompt de extracción reforzado. |
| **0.5.7** | Documentación con blob hero + acordeones plegables + progreso en vivo documento×motor. |
| **0.5.8** | Motion physics real (MotionScheme del tema), formas diferenciadas entre secciones, contadores animados. |
| **0.5.9** | Firma: `TabRow` → `SegmentedButton`, quitado campo `Profesión` sin uso. |
| **0.6.0** | Blob como CTA, pop-up modal para progreso, títulos "Paso N..." retirados en los 5 pasos, regla del prompt para DNI+CIF combinado. |
| **0.6.1** | Doble pop-up arreglado (Documentación tiene su Dialog rico, otros pasos usan el genérico). Pantalla de Documentación rellena todo el espacio sin huecos. Pop-up rico limpio (sin círculos redundantes). |
| **0.6.2** | Contexto de conjunto de documentos para el prompt (arregla clasificación errónea de DNI/NIE aislado como autónomo cuando hay CIF en el conjunto). Pop-up con `LoadingIndicator` squiggly de M3 Expressive arriba, en vez de logo duplicado. |
| **0.6.3** | Firma alineada con web: `ExpressiveAccordion` compartido, "Ajustes de firma" y "Huecos de firma" plegables, "Una a una" + "⚡ Todos", paleta ampliada a 6 tintas, checkbox "Mejorar con IA", botón "📷 Hacer foto" con permiso `CAMERA` en Manifest. |
| **0.6.4** | Firma: previsualización sin duplicados, scroll animado a página estampada, snackbar de feedback, flechas de navegación entre huecos como en la web. |
| **0.6.5** | **Persistencia de sesión (Fase 1)**: el progreso del wizard sobrevive al segundo plano y a la muerte del proceso. Botón "Empezar de nuevo" en Ajustes y "Empezar otro contrato" tras generar el PDF. Aviso si un URI restaurado ya no es accesible. |
| **0.7.0–0.7.2** | Recorte de firma (mapeo ContentScale.Fit), borrador de firma con deshacer, `MultiAiExtractor` agrupa TODO un archivo en una llamada por motor (antes: una por página), motores ya no mueren permanentemente por un error transitorio (solo 401/403/404). |
| **0.7.3–0.7.4** | Migración de servidor: URL de fábrica del proxy → `datingtrck.com`. Prompt reforzado (formato NIF/CIF/NIE, IRPF vs Sociedades como señal persona/empresa, censal individual). |
| **0.7.5** | `Ipv4PreferredDns`: prioriza IPv4 (dominio sin AAAA fallaba en 5G/NAT64 dentro de la app, no en Chrome). |
| **0.7.6** | Banco nunca es empresa (CIF de banco = tercero), alternativas para dirección de actividad, ver sugerencias alternativas en Relleno (dropdown por campo). |
| **0.7.7** | "Estructura detectada" en Paso 1 (páginas, campos, huecos de firma) + hotfix de scope en `ContractStep`. |
| **0.7.8** | **Tipo de documento por CONTENIDO**: `DocumentLoader.firstPagesText()` (PDFBox) + `DocumentTypeDetector.fromContent()` — el diálogo "Analizando con…" muestra "Certificado de situación censal", "Alta en RETA"… en vez de `document:17077`. Fix causa raíz SAF (`OpenableColumns.DISPLAY_NAME`); el nombre de archivo real viaja a la IA como contexto. |
| **0.7.9** | **Tipo por IA (visión)** para fotos/escaneos sin capa de texto: campo `tipo_documento` en el prompt (vocabulario cerrado), callback `onDocTypeDetected`; la detección local tiene prioridad. ⚠️ Pendiente replicar `tipo_documento` en el prompt de la app web (paridad). |
| **0.9.3** | El prompt lleva los **campos reales del PDF cargado** en vez de la lista fija `CANON` (causa de que se «olvidaran» campos), y una **guía de campos** cuando el contrato usa nombres propios — copiada literal de `tplHint` de la web, así que la paridad se mantiene. |
| **0.9.4** | **Fase 1 del roadmap multi-formulario** (`roadmap-multiformulario.html`): `PdfFieldInspector`, lee los widgets del AcroForm en orden de lectura real (página → fila con tolerancia 6pt → columna), coordenadas origen arriba-izquierda. Verificado contra el Modelo 145 (60 campos). Utilidad pura, sin UI ni cambios de comportamiento — base para la fase 2 (esquema dinámico). |
| **0.9.9** | **Fase 2 · tanda 3 de 3 — cierra la fase**: persistencia de esquemas por huella (`schemas_v1`), `Expediente`/`ExpedienteDocument` con datos compartidos por `CanonicalKeys`, y `SchemaMigration` desde `templates_v1`. La migración es **perezosa y no destructiva**: `templates_v1` no se toca, se convierte al pedir el esquema y volver atrás es dejar de leer la clave nueva. `WizardViewModel` sin tocar. |
| **0.10.0** | **Fase 2 cerrada del todo — `FormSchemaBuilder`**: detecta tablas por geometría (nunca por nombre), verificado contra los 4 PDFs de Aire (contrato: 5 tablas; portabilidad: 1 tabla 25×7; conectividad: 1 tabla de 10 filas, con el defecto de origen de filas 07/08 superpuestas reflejado, no tapado; SEPA: 0 tablas, correcto). Fichero nuevo, sin enganchar. |
| **0.10.1** | **Fase 3 — `FieldLabeler`**: etiquetado por visión, se pregunta por columna y no por celda (7 preguntas en vez de 175 en una tabla 25×7). `SchemaLabeling.apply()` nunca pisa `LabelSource.USUARIO`. Deuda: usa `task=locate_signature` del proxy por no haber una tarea propia. Fichero nuevo, sin enganchar. |
| **0.10.2** | Hueco detectado planificando la fase 4: `FormSchemaBuilder` nunca emitía `FieldKind.RADIO` (todo botón salía CHECKBOX/TEXT), así que no existía ningún grupo de opción en ningún esquema. Ahora `PdfFieldInspector` lee `isRadio`/`onState` por widget (verificado contra el fuente real de `pdfbox-android 2.0.27.0`, tag `v2.0.27.0` — no se repite el error de la 0.9.8.1 de asumir un método inexistente) y `FormSchemaBuilder` los propaga. `name` sigue siendo la clave de agrupación; no hace falta campo nuevo. |
| **0.10.6** | **Fase 5, tandas 5·0 y 5·1**: `AcroFormFiller` deja de inyectar el responsable de Orange sin condición (con cualquier otro PDF ese campo no existe y acababa SIEMPRE en `missingFields`: un «falta un campo» falso en cada prueba con Aire), y `FillStep` deja de tener las secciones escritas dentro — pasan a `FillSections.kt` y se reciben como parámetro. Es la costura por la que entrará el `FormSchema` en la 5·4. Cero cambios visibles en Orange, verificado con una prueba que compara contra la lista original transcrita literal. |
| **0.10.7** | **Fase 5, tanda 5·2**: `FieldValidator.validate()` decide primero por `BuiltinSchemas.canonicalFor(fieldName)` en vez de por heurística sobre el nombre del campo (`docs/PLAN_FASE_5.md`, hallazgo 2.5) — evita que la validación se apague en silencio en cuanto la 5·4 traiga campos de Aire. El hermano de Provincia para el CP y `FECHA_KEYS` en `FillStep` dejan de asumir la convención de nombre `_2`/literal de Orange y se resuelven por canónica (`BuiltinSchemas.realKeyFor`/`provinciaKeyFor`), hallazgo 2.6. Fuente todavía `CANON` (5·3/5·4 la sustituyen). ⚠️ Efecto colateral, no una regresión: dos campos de `CANON` tenían la validación rota desde antes de esta tanda por un desajuste de espacios en la comparación por nombre (`"NIF representante"` normalizaba a `"nif representante"`, con espacio, y se comparaba contra `"nifrepresentante"`; iban nunca casaba con `"datosbancarios"` por el mismo motivo) — con la canónica sí validan. Ver sección 5 de `CONTINUIDAD.md`. |
| **0.10.8** | **Fase 5, tanda 5·2b**: cierra los cuatro (cinco) acoplamientos a nombres de campo de Orange que quedaban repartidos y que la 5·3 habría cambiado por debajo. `FieldNormalizer.normVal` decide por canónica (mismo patrón que la 5·2 en `FieldValidator`), `DateAutofill` resuelve sus tres claves con `BuiltinSchemas.realKeyFor`, `copyFiscalToComercio` usa `fiscalToComercioKeyPairs()` en vez de concatenar `_2`, `keyboardFor` decide por canónica, y el `coverageKeys` de `MultiAiExtractor` usa `DATE_KEYS` en vez de repetir el literal. Cuatro de las cinco son cero-cambio en Orange (verificado: `realKeyFor` devuelve los mismos literales). ⚠️ `normVal` sí cambia en tres campos, por el **mismo bug de espacios** que destapó la 5·2 (los nombres de dos palabras normalizan con espacio y se comparaban sin él): ahora el IBAN se compacta, el NIF del representante se limpia y «Apellidos, Nombre» se invierte. El IBAN compactado **se ve en el PDF final** — es paridad con la web, no una regresión. Se hace ANTES de la 5·3 a propósito: con las heurísticas ya colgadas de la canónica, la migración de clave llega a terreno despejado. |
| **0.10.9** | **Fase 5, tanda 5·3 — «la clave»**: `fieldValues`/`fieldStates`/`fieldOrigins`/`fieldCandidates`/`UndoEntry` se indexan por **nombre real** del campo del AcroForm, y `fieldMapping` **deja de pasarse a la salida** (`AcroFormFiller` ya no traduce nada). Pieza nueva `FieldKeys` como única capa de traducción `clave de CANON <-> nombre real`; en la 5·4 su fuente pasará a ser el `FormSchema` y será el único sitio que tocar. **Arregla un fallo vivo en producción**: desde la 0.9.3 el prompt pedía los nombres reales del PDF cargado, así que con un PDF propio la IA ya guardaba por nombre real mientras `FillStep` seguía leyendo por clave de Orange — **el paso de Relleno mostraba vacíos los campos que la IA sí había extraído** (sólo se veían fecha y responsable, que eran los que la app inyectaba con clave de Orange). El PDF final salía bien por casualidad, vía el `?: canonical` de `realName()`. Migraciones: `PersistedWizardState` v1→v2 reindexando con el `fieldMapping` que el propio DTO ya guardaba, y `ContractProfile` gana `version` + `migrated()` **perezoso** (cada perfil lleva dentro su `fieldMapping`, así que no hay perfiles huérfanos). Las fechas y el responsable ya no se escriben si el formulario no tiene esos campos (`realIfPresent`) — el fallo que la 5·0 arregló para el responsable y seguía vivo en las fechas. Y una **auditoría sobre el repo completo** destapó dos consumidores más que seguían en claves de Orange y que sí importan: `AutoFillPolicy.RISKY_SOURCES` —con un PDF propio **la protección se apagaba en silencio y el IBAN de un contrato de alquiler (el del arrendador) se habría autorrellenado como dato verificado**— y `FieldResolver` (claves de los paquetes de dirección, `DATE_KEYS`, y el `decide()` que llama a la política). Los dos migrados a clave canónica. En Orange `FieldKeys` es la identidad: verificado con `kotlinc -Werror` (cero avisos) y 58 comprobaciones de comportamiento ejecutables, 0 fallos. |
| **0.10.5** | **Cierra la fase 3**: `VisionLabelPass` orquesta el etiquetado por visión sobre un `FormSchema` entero (una llamada por página CON huecos, tandas de 24 objetivos, sin preguntar celdas ni lo corregido a mano) y el editor gana un botón «Etiquetar con IA». Arregla un desajuste que sólo aparecía al enganchar: `FieldLabeler` manda ids opacos a propósito pero `SchemaLabeling` indexa por nombre de campo, así que **no se habría aplicado ninguna etiqueta y sin error alguno**; se traducen los ids en el orquestador, sin tocar ninguna de las dos piezas. Verificado con `kotlinc` en local, no sólo con `grep`. |
| **0.10.4** | **Fase 4 cableada — el editor deja de estar huérfano**: `LabelEditorViewModel` (orquestador propio, no `WizardViewModel`) + `LabelEditorScreen` + acceso en Ajustes › Herramientas (beta) + ruta `etiquetas`. Camino completo: SAF → `PdfFieldInspector.inspect()`/`pageCount()` → huella → esquema guardado si existe, o `FormSchemaBuilder.build()` → `LabelEditor` → `schemas_v1`. Añade `FieldRect` (`FormField.rect` y `TableColumn.rect`/`page`), que da la geometría que el esquema perdía y que la fase 3 necesita para recortar; opcional, así que `SCHEMA_VERSION` no se toca. El asistente sigue sin tocarse. |
| **0.10.3** | **Fase 4 (la de verdad)**: `SchemaEditing` (ediciones puras — campo/grupo de opción por `name`, columna entera, título de sección — siempre marca `LabelSource.USUARIO`) + `LabelEditor` (UI). Ficheros nuevos, aún nada los invoca con un PDF real del usuario — falta el cableado (ver Próximas tandas). No confundir con `MappingEditor.kt`, del flujo legado Orange/CANON. |
| **0.9.8.2** | **Nombres consistentes**: commit, run de Actions, zip del artefacto y APK pasan a llamarse igual (`rellenador-<versionName>`). El APK deja de ser `app-debug.apk` y se quita `run-name`, que metía el mensaje entero del commit en el título del run. |
| **0.9.8.1** | **Arreglo de compilación**: las 0.9.7 y 0.9.8 se subieron sin build verde y no compilaban. `PDRadioButton.getSelectableValues()` no existe en `pdfbox-android 2.0.27.0` (reescrito con `PDCheckBox.check()`/`unCheck()` y `PDButton.getOnValues()`), y el enum `FieldOrigin` chocaba con el `data class` homónimo de `ui.wizard` que importan `FieldResolver` y `AutoFillPolicy` (renombrado a `ValueOrigin`). |
| **0.9.8** | **Fase 2 · tanda 2 de 3**: `FormSchema.kt` — modelo de esquema dinámico. `CanonicalKeys` (vocabulario transversal del expediente), `ValueOrigin` (DOCUMENTO/AJUSTES/PLATAFORMA/CATALOGO/CALCULADO/FIRMA), `FormField` con `onState`, `optionLabel` y `combGroup`, `FormSection` con SIMPLE/TABLE/REPEATED_BLOCK, `TableColumn`/`TableRow` definidos por geometría, y `BuiltinSchemas.orangeDistribution()` derivado de `CANON`. Sólo estructuras nuevas: no se persiste ni se usa aún. |
| **0.9.7** | **Fase 2 · tanda 1 de 3**: las casillas se marcaban asumiendo el estado `/On`, que no existe en ninguno de los PDFs de Aire (usan `Sí`, `Opción1/2`, `0`…`5`). Nuevo `applyButtonValue()` que resuelve el estado contra el propio documento. Además, el fallo al marcar una casilla era silencioso (`runCatching` sin `onFailure`) y ahora se reporta en `missingFields`. |
| **0.9.6** | **Saneamiento previo a la fase 2**: corregido el orden de lectura del `PdfFieldInspector` (agrupaba filas troceando el eje Y en tramos fijos y partía filas; detectado en la fila del BIC del SEPA de Aire, arreglado agrupando por hueco respecto al ancla de fila). Verificado contra los 4 formularios de Aire: 0 posiciones cambian en el contrato de 488 campos. Además, la tarjeta del contrato Orange se oculta (bandera `SHOW_LEGACY_DEFAULT_CONTRACT`) sin borrar el camino `DEFAULT`, que sigue funcionando si el PDF se sube. |
| **0.9.5** | **Desacoplo de Orange + paleta Aire**: paleta de marca muestreada del PDF real de Aire (`#9F0BFF`/`#00095A`/`#ECD0FF`); el contrato Orange pasa de "por defecto" a "heredado" (sigue funcionando igual, ya no es la opción sugerida); copy de UI y prompt genericizados. Cero cambios en extracción/relleno/firma. Analizado `Contrato_empresas.pdf` de Aire: 481 campos AcroForm reales, incluye 4 campos `/Sig` (tipo nuevo, sin manejar hoy — relevante para fase 6) y bloques de nombres autogenerados sin etiqueta (confirma que la fase 3 de etiquetado por visión es imprescindible). |
| **0.9.2** | **Regresión de la 0.8.7 corregida**: `getType()` devuelve null para `file://`, y desde que `DocumentStore` copia los documentos a `filesDir` todos los URIs lo son → todo caía en `octet-stream` y el análisis fallaba con «No se pudieron leer los documentos». El MIME se deduce ahora por extensión cuando el resolver no sabe. |
| **0.9.1** | **Aviso previo (RGPD) antes de mandar documentos a la IA**: `ConsentSheet` con los motores separados por región, casilla obligatoria y «no volver a preguntar» persistido. **Modo solo motores europeos**: apaga y bloquea los de fuera de la UE. Ambas preferencias sobreviven a «Empezar otro contrato». |
| **0.9.0** | **Regresión corregida**: los fallos de motor eran invisibles desde la v0.8.0 (se borró con `ReviewStep` el único panel que los mostraba). Nuevo `EngineFailure` que traduce el error crudo a causa legible + consejo, y aviso plegable en el hero de Relleno. |
| **0.8.7** | **Persistencia de documentos (Fase 2)**: nuevo `DocumentStore` que copia los documentos a `filesDir/docs/` al añadirlos, así sobreviven a la muerte del proceso. Limpieza al quitar un documento y al empezar contrato nuevo. |
| **0.8.6** | Cuatro fallos de uso real: scroll que faltaba en Documentación (+ scroll anidado retirado), hoja de Ajustes desplegada del todo (`skipPartiallyExpanded`), "Dejar en blanco" ahora vacía de verdad el campo, y la fecha del contrato es la de la firma y no la extraída de los documentos (`DATE_KEYS`). |
| **0.8.5** | Paso de Firma: hueco reservado para el snackbar (overlay anclado abajo que tapaba los últimos controles) y previsualización con altura proporcional a la pantalla (62%, entre 320 y 560 dp) en vez de 560 dp fijos. |
| **0.8.4** | `removeFrameLines()` en `SignatureProcessor`: la firma extraída de foto ya no arrastra el recuadro impreso ni la raya de pauta. Criterio triple (cobertura ≥75% · grosor ≤4 · pegada al borde o span ≥90%), calibrado contra una firma real cuyo trazo vertical cubre el 78% de la altura. |
| **0.8.3** | Hero de la IA en la cabecera del Relleno (titular, documentos y motores, contador `X/N` con rebote real vía `Animatable` + specs capturados fuera del `LaunchedEffect`, barra animada). Cierra el diseño aprobado. |
| **0.8.2** | Aspecto del mockup aprobado: roles `surfaceContainer*` cálidos en `Theme.kt` (faltaban → M3 los derivaba en gris neutro y las tarjetas se veían frías sobre fondo cálido; afecta a toda la app), stepper de barras en vez de círculos, campos como cajas rellenas con tinte por estado y chip de procedencia. Limpieza de imports huérfanos. |
| **0.8.1** | Conecta `flagIntruders` (la detección de documento de otro titular estaba escrita pero sin llamar) con deducción del titular por documento y regla de "ante empate no se acusa"; persiste `fieldStates`/`fieldOrigins`/`fieldCandidates` (sin ellos, restaurar sesión desactivaba el bloqueo del avance); snackbar con DESHACER y badge "N por decidir" por sección. |
| **0.8.0** | **Asistente de 4 pasos**: "Revisión IA" se funde en "Relleno". `ReviewStep.kt` eliminado; el formulario llega prerrellenado con estado por campo (`AI`/`CONFLICT`/`WARN`), procedencia por documento, hoja de decisión con alternativas, deshacer, y bloqueo del avance mientras haya campos por decidir. Autorrelleno gobernado por `AutoFillPolicy` (procedencia, no solo consenso de motores). Migración de sesiones persistidas (`schemaVersion` + `migrateStepIndex`). |
| **0.7.10** | **Fallback DNS-over-HTTPS** en `Ipv4PreferredDns`: ante caché DNS negativa del router/dispositivo (hasta 24 h por el TTL del SOA), la app resuelve por su cuenta vía `1.1.1.1`/`8.8.8.8` por IP literal, TLS intacto, sin dependencias nuevas. |

---

## Próximas tandas

### 🔴 Alta prioridad

- ~~**Cablear el editor de etiquetas al mundo real**~~ ✅ *Completado en v0.10.4* — Ajustes ›
  Herramientas (beta) → SAF → `PdfFieldInspector` → huella → `schemas_v1` o
  `FormSchemaBuilder.build()` → `LabelEditor` → persistido. `FieldLabeler` quedó fuera a
  propósito (ver la entrada siguiente); el asistente sigue sin tocarse.

- ~~**Enganchar `FieldLabeler` al cableado (etiquetado por visión)**~~ ✅ *Completado en v0.10.5* —
  `VisionLabelPass` + botón «Etiquetar con IA» en el editor. Resultó que no hacían falta recortes:
  `FieldLabeler` quiere la página entera con los rectángulos en porcentaje, y para eso sirve
  `FieldRect`. Queda pendiente **probarlo contra un PDF real de Aire en el móvil**: la lógica está
  verificada en local, pero la calidad de las etiquetas que devuelve la visión sólo se ve usándolo.

- **Tarea `label_fields` en el proxy.** Deuda heredada de la 0.10.1 y no resuelta en la 0.10.5:
  `FieldLabeler` usa `task = "locate_signature"` porque es la única tarea de visión que expone
  `ai-proxy.php`. Funciona, pero el nombre engaña a quien lea el código o el log del servidor.
  No depende de esta app: hay que tocar el proxy, que se despliega por FTP/cPanel.

- **Unificar las tres aperturas del PDF en `LabelEditorViewModel.pickPdf()`**. Hoy abre el
  documento tres veces (campos, nº de páginas, nombres). Pide una API del inspector que devuelva
  las tres cosas de una pasada. Tanda pequeña, sin riesgo.

- **Fase 5 — relleno dinámico**: conectar `FillStep`/`WizardViewModel` al `FormSchema` en vez de
  a las 6 secciones fijas de `CANON`. **Requisitos acordados (2026-09-02)**: al cerrar la 5·4, en
  vez de los 21 campos de Orange aparecen todos los del PDF subido, agrupados por secciones y en el
  orden del PDF, tanto en el mapeo como en el relleno. El alta de Aire cubre sólo las páginas 1 y 3
  del contrato y marca únicamente la casilla ALTA NUEVA. Detalle en `docs/PLAN_FASE_5.md` §6. **Planificada en detalle en `docs/PLAN_FASE_5.md`**
  (2026-08-31, leyendo el código): se parte en **seis tandas** por riesgo, y el orden no es por
  tamaño — la migración de datos (tanda 5·3) va **antes** de cambiar lo que se dibuja (5·4), para
  que se migre con una referencia visible contra la que comparar. Leer ese documento antes de
  tocar nada; incluye tres hallazgos que no estaban aquí, entre ellos que la validación se apagaría
  en silencio con los nombres de campo de Aire.


- ~~**Persistencia de documentos (Fase 2 de robustez)**~~ ✅ *Completado en v0.8.7* — `DocumentStore`
  copia los documentos a `filesDir/docs/` al añadirlos. Texto original:
  Copiar los documentos aportados a almacenamiento privado de la app al añadirlos (o
  llamar a `takePersistableUriPermission` cuando sea posible con el picker usado). Hoy,
  si el proceso muere en segundo plano, los `Uri`s persistidos pueden volverse inaccesibles
  y el usuario tiene que volver a añadir los documentos (con un aviso claro, eso sí).
  Esta tanda lo resuelve del todo copiando los bytes a `getExternalFilesDir` o
  `context.filesDir` — un directorio propio de la app que no depende del proveedor del URI.
  *Fichero clave*: `DocumentLoader.kt`, `WizardViewModel.addDocuments`, `WizardUiState.docUris`.

- ~~**Subir `ai-proxy.php` corregido a producción**~~ ✅ *Completado (ago 2026)* — el
  proxy con todos los fixes acumulados ya vive en `datingtrck.com/pdf/ai-proxy.php`
  (BanaHosting/cPanel, usuario `obvzudpy`), con la config en
  `/home/obvzudpy/datingtrck.com/proxyconfig/` (carpeta 403). Verificado en vivo:
  GET responde `ok:true` con `gemini:true`/`groq:true`.

- **Paridad web: campo `tipo_documento` en el prompt**
  La v0.7.9 añadió `tipo_documento` al `ExtractionPrompt` de Android. La regla del
  proyecto es que el prompt sea VERBATIM entre web y Android → hay que replicar el
  campo (con el mismo vocabulario cerrado) en el prompt de `rellenador-pro.html`.
  Requiere que Pablo suba el fichero web actual al arrancar la sesión.

- **Endurecer `.htaccess` de `proxyconfig/`**
  Hoy la carpeta da 403 pero el fichero `ai-proxy.config.php` da 200 con cuerpo vacío
  (PHP lo ejecuta). Si algún día PHP dejara de ejecutarse en esa carpeta, el fichero se
  serviría en texto plano CON LAS CLAVES. Añadir denegación explícita del fichero
  (`<Files>`/`Require all denied`) además del bloqueo de carpeta. Tanda de 5 minutos.

### 🟠 Media prioridad

- **Modo "De documento" en firma**
  Añadir el 3.º tab del paso de Firma (Web ya lo tiene): permite elegir un PDF de los que
  se subieron en el Paso 2 y recortar la firma directamente de ahí. Implica enlazar
  `docUris` del Paso 2 con un selector/recorte en el Paso 5. Es una tanda pequeña propia.

- **APK release firmado**
  Hoy el workflow solo compila `debug`. Falta añadir `signingConfig` con un keystore
  (guardado como secreto en el workflow) y un job/step separado que compile `release`
  para distribuir el APK como instalable "real" en dispositivos que verifiquen firma.

- **Persistencia exportar/importar perfil**
  El backend ya existe (`PrefsRepository.exportProfileJson` / `importProfileJson`), pero
  no hay UI en Ajustes que lo exponga. Solo hay que añadir dos botones y sus launchers.

### 🟡 Media/baja prioridad

- **Logos oficiales de proveedor**
  Hoy son placeholders (círculo con inicial + `brandColor`). El fichero `LOGOS_TODO.md`
  del repo tiene el listado de los SVGs oficiales que hay que meter en
  `res/drawable/ic_provider_*.xml`. Cuando se hagan, `ProviderLogo` los prefiere
  automáticamente sobre el placeholder.

- **`responseSchema` estricto para Gemini**
  Gemini Pro sugirió pasar a JSON estructurado nativo (`responseMimeType: application/json`
  ya se aplica; el `responseSchema` completo no). Rechazado por ahora porque requiere que
  todos los motores (Claude, Groq, Mistral, EUrouter) devuelvan el mismo formato o mantener
  dos parsers. Cuando toque hacer un refactor de `AiJsonParser` unificando el formato de
  todos los motores, este cambio entra a la vez.

- **PDF nativo o TEXTO extraído, en vez de rasterizar siempre a imagen**
  Hoy el cliente Android SIEMPRE rasteriza cada página con `PdfRenderer` a JPEG antes de
  mandar, sea cual sea el motor. Dos alternativas, no excluyentes entre sí:
  - **PDF nativo**: Gemini y Claude aceptan `inline_data` con el PDF real (sin rasterizar).
  - **Texto extraído**: para PDFs con capa de texto real (no escaneados — como los
    certificados de la AEAT, que tienen texto seleccionable), extraer el texto y
    mandarlo en vez de una imagen. La app web ya hace esto para Groq (`pdfTextOf()` en
    `rellenador-pro.html`) — Android nunca lo ha aprovechado. Ventajas reales: mucho
    más barato en tokens (ayudaría directamente con el límite de 8000 TPM de Groq),
    más preciso (sin errores de interpretación visual/OCR), y más rápido.
  La librería ya NO es un bloqueo: desde la v0.7.8, `DocumentLoader.firstPagesText()`
  extrae texto con PDFBox (`PDFTextStripper`) — hoy solo para tipificar el documento,
  pero es exactamente la pieza que esta tanda necesitaría para mandar texto a los
  motores en vez de imágenes. Si se ve que la extracción
  pierde precisión por no ver la estructura completa del PDF, o si se quiere ahorrar
  coste/tokens en documentos de texto, esta tanda migra a doble ruta según el tipo de
  motor y si el PDF tiene capa de texto aprovechable.

### 🟢 Baja prioridad (deferred de siempre)

- **Pinch-to-zoom en la miniatura de firma**
- **Controles de posición del sello por página individual** (hoy solo la página 24 tiene
  sliders; el resto se calcula con `stampFor` desde los anchors calibrados)
- **Slider de tamaño global de firma** — descartado en la tanda 0.6.3 porque requeriría
  refactor mayor de `stampFor()` para introducir un multiplicador global.

---

## Pendiente de decisión de Pablo

- **`"Profesión puestos de trabajo..."`** — descartado por Pablo en la tanda 0.5.9. Si
  algún día se necesita, la única acción es volverlo a añadir a `ContractFields.CANON`
  y a la sección Empresa/Identificación de `FillStep.kt`. El campo existe en el AcroForm
  real del contrato, solo estaba sin uso claro.

---

## Aprendizajes técnicos preservados

Estos son errores concretos que ya nos costaron una tanda de build rojo — no repetirlos:

- `MaterialTheme.motionScheme` es `@Composable`, **no se puede llamar dentro de
  `LaunchedEffect`** (suspend). Capturar en variable antes.
- `Icons.Outlined.Cpu` **no existe** en Material Icons. Usar `Icons.Outlined.Memory`.
- Gemini `thinkingLevel` y `thinkingBudget` son **mutuamente excluyentes** (mandar los dos
  da HTTP 400).
- Gemini 3.x tiene thinking activo por defecto — se come tokens de salida. Poner
  `thinkingLevel: "low"` y subir `maxOutputTokens` a 8192 mínimo.
- Página 24 del contrato **no tiene AcroForm fields** — hay que force-includirla con
  `applyKnownContractFixes()` o el detector no la ve.
- Nombres de campo con **doble espacio importan**: `"Nombre  Razón Social"`,
  `"Email  Facturación"`. `norm()` los preserva, no se debe "limpiar" ese espacio doble.
- Checkbox del tipo de identificación NIE se llama literalmente `"undefined"` en el
  AcroForm real (bug del PDF original). Ya está manejado como `CHECKBOX_NIE = "undefined"`.
- `unzip -o` **no borra archivos**, solo sobrescribe. Cualquier eliminación de fichero
  hay que hacerla con `git rm` explícito.
- Web fetch a GitHub **puede devolver cache antiguo**. Verificar HEAD real vía MCPGIT
  antes de asumir el estado del repo.
- `ContentResolver.getType()` **solo resuelve `content://`**: con `file://` devuelve null.
  Al copiar los documentos a almacenamiento propio, TODOS los URIs pasan a ser `file://`,
  así que cualquier lógica que dependa del MIME del resolver deja de funcionar de golpe.
- En un `content://` de SAF, `uri.lastPathSegment` **NO es el nombre del fichero** — es
  el ID crudo del proveedor (`document:17077`). El nombre real se consulta con
  `OpenableColumns.DISPLAY_NAME` vía `ContentResolver`.
- `kotlinx.serialization` **omite los campos con valor por defecto** salvo
  `encodeDefaults = true` en el `Json` — un campo "siempre presente" en el modelo puede
  no viajar nunca en la petición real (causa raíz del 500 histórico de Gemini por
  `gemini_mode` ausente).
- Una **caché DNS negativa** (dispositivo o router) puede dejar la app sin resolver un
  dominio hasta 24 h (TTL negativo del SOA) aunque el dominio esté perfecto — de ahí el
  fallback DoH de la 0.7.10. Los endpoints DoH deben ir por **IP literal** o necesitan
  DNS para resolverse a sí mismos.
- La detección de tipo de documento por nombre de archivo es inútil en el flujo real:
  los clientes mandan todo por WhatsApp (`DOC-…-WA….PDF`, `IMG-…-WA….jpg`, sin pista).
  Por contenido (texto del PDF) sí funciona; fotos/escaneos sin texto requieren visión.
