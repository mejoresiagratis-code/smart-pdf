# Continuidad — Rellenador PDF (Android)

> **Para qué es esto.** Arranque de sesión nueva con Claude. Pégale el enlace de este fichero
> o su contenido; con eso y el repo tiene el contexto completo. **No hace falta reenviar los
> PDFs de Aire**: su análisis está en `docs/ANALISIS_FORMULARIOS_AIRE.md`.
>
> **Actualizado**: 2026-09-04. Último commit de código: **0.10.27-etiquetado-por-tandas**
> (versionCode 97). Verdes hasta la **0.10.17**; la **0.10.19 salió ROJA** (tres errores de
> compilación), la **0.10.20** los arregla y está **verde**. La **0.10.21 sigue sin confirmar**
> (se subió para probar en el móvil, no se ha sabido el resultado). **Pablo confirmó verdes la
> 0.10.22 y la 0.10.23** en el móvil, y de ese mismo QA salieron los reportes que arreglan la
> 0.10.24 y la 0.10.25, ambas verdes. Del QA con el contrato de Aire real (capturas + PDF
> resultante) salió la **0.10.26**, sin confirmar. No hubo build de la
> 0.10.18 (ver §1). Este documento
> **caduca**: la primera regla de abajo existe precisamente porque lo que aquí se afirma puede
> estar viejo.

Habla en **español de España**.

---

## 1. Lo primero, siempre

Clona el repo y **verifica el estado real por `git log`. No te fíes de ningún documento,
incluido este.**

```
git clone https://github.com/mejoresiagratis-code/smart-pdf
cd smart-pdf && git log --oneline -8
```

El último commit **de código** debería ser `0.10.27-etiquetado-por-tandas` · versionCode 97.
Por encima puede haber commits solo de documentación, que no suben versión. Si el último código
no es ése, este documento está desfasado: manda `git log` y la cabecera del `CHANGELOG.md`.

### Estado del build

De la 0.10.6 a la **0.10.17, todas verdes** (las 0.10.14 a 0.10.17 confirmadas por Pablo). La
**0.10.19 salió ROJA** y la **0.10.20 la arregló y está verde** (run 33724263803, comprobado por
API). La **0.10.21 sigue sin confirmar en el móvil**. **La 0.10.22 y la 0.10.23 las confirmó
Pablo probándolas en el móvil** — de ese mismo QA salieron los reportes que arreglan la 0.10.24 y
la 0.10.25 (ver tabla), las dos **verdes** (runs 33818151135 y 33819933422). Del QA con el
contrato de Aire real y el PDF resultante salió la **0.10.26**, que está **sin confirmar**:
pregunta el resultado antes de abrir tanda nueva, porque la regla es una tanda, una versión, un
build verde antes de la siguiente.

**La 0.10.18 nunca tuvo build.** El `versionCode` va 87 (0.10.17) → 89 (0.10.19): el 88 no
existe y el código de la 5·4f entró **dentro** del commit de la 0.10.19 (`c898892`). Si en algún
sitio lees «la 0.10.18 está pendiente de confirmar», es una pregunta sin respuesta posible: lo
que se construyó fue 0.10.18 + 0.10.19 juntas, primero en rojo y luego en verde con la 0.10.20.

**Despliegue sin tokens** (§7): las tandas de la 0.10.15 en adelante se han pasado en un zip.
**Ojo con los zips**: `.gitignore` tiene `*.zip`, pero cuando el zip se sube por la web de GitHub
(«Add files via upload») el `.gitignore` **no se consulta** y entra igual. Así se colaron cuatro
—0.10.15, 0.10.17, 0.10.19 y 0.10.20, 376 KB— y se han quitado con `git rm` en la 0.10.21. Si
vuelves a subir un zip por la web, bórralo después.

La 0.10.12 se subió **sin typecheckear en local** (es todo Compose, y aquí no hay SDK ni Maven) y
salió verde igual. No lo tomes como precedente: para Kotlin puro el `kotlinc` de las releases de
JetBrains sí sirve y ha evitado dos builds rojos — ver §6. La 0.10.13 y la 0.10.15 sí se
typecheckearon y llevan comprobaciones ejecutables (27 y 21 respectivamente, más las portadas a
`app/src/test`). La 0.10.16 es Compose y no se typecheckeó; la 0.10.17 y la 0.10.18 son Kotlin
puro con pruebas en `app/src/test` (5 y 8 casos) pero tampoco se typecheckearon en local — no
había `kotlinc` instalado en esa sesión. La 0.10.21 sí: `FillSections.kt` typecheckeado con
`kotlinc 2.1.0 -Werror` y sus 5 casos portados a ejecutable, 8/8; `FillStep.kt` es Compose y va
sólo con balance sintáctico y revisión de símbolos. La 0.10.22 **no se typecheckeó**: la sesión
sólo tenía red hacia `archive.ubuntu.com` y el `kotlinc` de apt es la 1.3.31, que no soporta las
comas finales (`trailing commas`) que usa todo el proyecto — falla con `error: expecting an
argument` en ficheros ni siquiera tocados. Se revisó a mano (símbolos existentes, paréntesis y
llaves balanceados, sin comas finales en los ficheros nuevos) y queda en manos de Actions, como
con el Compose. La 0.10.23 (mitad 2, Compose) tampoco se typecheckeó, por el mismo motivo — sólo
balance de paréntesis/llaves/corchetes (exacto: 499/499, 194/194, 19/19 en `FillStep.kt`) y
revisión de que cada símbolo nuevo (`AffinityGroup`, `confirmAffinity`, `activeSchema`) existe de
verdad.

Lo siguiente **no es código**: es probar en el móvil lo que ya está subido. Ver sección 5.

---

## 2. Qué leer, en este orden

1. `ROADMAP.md` — plan multi-formulario, decisión de arquitectura de expediente, fases.
2. `docs/ANALISIS_FORMULARIOS_AIRE.md` — análisis de los PDFs de Aire, verificado contra los
   ficheros reales. Es el brief técnico de las fases que quedan.
3. `docs/PLAN_FASE_5.md` — plan de la fase 5 partido en tandas, escrito leyendo el código.
   **De lectura obligada antes de tocar el asistente.** Su §6 tiene los requisitos acordados para
   la 5·4 (secciones en el orden del PDF, alcance del alta, casilla de ALTA), ya ejecutados.
4. `docs/PLAN_ETIQUETADO_ORGANICO.md` — plan de la 5·4b, **ya ejecutada** (0.10.13 y 0.10.14):
   que las secciones y los campos se llamen como en el papel. Escrito midiendo sobre el contrato con
   `pypdf`/`pdfplumber`, con los números reproducibles; incluye el fallo de orden que dejó la 5·4
   y las reglas de higiene (pulsadores y `/Sig` fuera del esquema, valor troceado en N casillas).
   **Si vas a tocar el esquema o el mapeo, éste es el documento.**
5. `docs/roadmap-multiformulario.html` — el roadmap de las 7 fases con el **estado real** de cada
   una, qué falta y por qué. Ábrelo en el navegador; la primera pestaña es el resumen.
6. `CHANGELOG.md`, entradas **0.9.4 a 0.10.20**.
7. `docs/ESTADO_Y_GUIA_DE_CONTINUIDAD.md` — arquitectura e historia. **Ojo**: su bloque
   «ESTADO ACTUAL» se quedó en `v0.7.10` / versionCode 48 (agosto de 2026), 26 versiones por
   detrás. La parte de arquitectura y la de «archivo histórico» siguen siendo útiles; el estado
   por versión está en el `CHANGELOG.md`, que es la fuente de verdad.

---

## 3. Dónde estamos

Pablo ya no trabaja con Orange/MASORANGE. La empresa nueva es **Aire Networks** (airetech.es),
con cuatro formularios rellenables propios: contrato de empresas (481 campos), portabilidad fija
(202), conectividad (141) y mandato SEPA (19).

### Dos pantallas con IA que no hay que confundir (aviso de Pablo, 2026-09-04)

Hay dos botones de IA en momentos distintos del asistente, y es fácil liarlos entre sí:

- **«Etiquetar con IA»** y **«Asignar mis datos con IA»** — en **«Revisar mapeo»**
  (`LabelEditor`/`LabelEditorViewModel`), accesible desde el paso 1 (Contrato), **antes** de subir
  ningún documento. El primero pone ETIQUETAS (`FieldLabeler`/`VisionLabelPass`, visión sobre la
  plantilla en blanco); el segundo propone CANÓNICAS (`CanonicalMapper.propose`, texto puro,
  ningún dato del cliente). Ninguno de los dos mete un valor en ningún campo.
- **Relleno** (`FillStep`/`WizardViewModel.runExtraction()`), después de subir documentación
  (DNI, censal, 036…): aquí es donde se EXTRAEN VALORES de los documentos y se autorrellenan los
  campos (`FieldResolver.resolve`), usando lo que se haya mapeado antes en «Revisar mapeo».

Son pantallas y objetos de código distintos. Antes de hablar de un fallo en una, comprobar en cuál
de las dos capturas/pasos está pasando.

| Versión | Qué |
|---|---|
| 0.9.4 | `PdfFieldInspector` (fase 1) |
| 0.9.5 | Paleta Aire; Orange deja de ser «por defecto» |
| 0.9.6 | Bug de orden de lectura; tarjeta de Orange oculta |
| 0.9.7 | Fase 2·1: estado real de las casillas (`/AP /N`) |
| 0.9.8 | Fase 2·2: `FormSchema.kt`, el modelo |
| 0.9.8.1 | Arreglo: 0.9.7 y 0.9.8 no compilaban |
| 0.9.8.2 | Nombres consistentes (commit = run = artefacto = APK) |
| 0.9.9 | Fase 2·3: persistencia, migración perezosa, `Expediente` |
| 0.9.9.1 | CI más rápido (`cmdline-tools` fijadas) |
| 0.10.0 | `FormSchemaBuilder` — tablas por geometría |
| 0.10.1 | Fase 3: `FieldLabeler` — etiquetado por visión |
| 0.10.2 | `FormSchemaBuilder` nunca emitía `FieldKind.RADIO`; ahora sí |
| 0.10.3 | Fase 4: `SchemaEditing` + `LabelEditor` (ficheros nuevos, sin enganchar) |
| 0.10.4 | Fase 4 **cableada**: el editor ya es alcanzable con un PDF real · verde ✅ |
| 0.10.5 | Fase 3 **cerrada**: `VisionLabelPass` engancha el etiquetado por visión · verde ✅ |
| 0.10.6 | Fase 5, tandas **5·0 y 5·1**: campo fantasma arreglado + costura de secciones ⚠️ sin verde |
| 0.10.7 | Fase 5, tanda **5·2**: validación, hermano del CP y `FECHA_KEYS` cuelgan de `canonical` · verde ✅ |
| 0.10.8 | Fase 5, tanda **5·2b**: `normVal`, `DateAutofill`, copia fiscal, teclado y cobertura, por canónica · verde ✅ |
| 0.10.9 | Fase 5, tanda **5·3**: la clave pasa a ser el nombre real del campo; `FieldKeys`; migración v1→v2 · verde ✅ |
| 0.10.10 | Fase 5, tanda **5·4**: `FillStep` y `MappingEditor` se dibujan desde el `FormSchema` del PDF; `BuiltinSchemas.recognize()`; casillas de cabecera de Aire · verde ✅ |
| 0.10.11 | Procedencia y nombre: inversión «Apellidos, Nombre» por la última coma; el domicilio de un documento de identidad deja de autorrellenarse · verde ✅ |
| 0.10.12 | Paso 1: nombre del fichero en la tarjeta, «Revisar mapeo» abre el panel del editor de etiquetas, y se puede revisar siempre · verde ✅ |
| 0.10.13 | Fase 5, tanda **5·4b**: etiquetado orgánico — `LayoutTextExtractor`, títulos de sección del texto del PDF, secciones por intervalo entre anclas, `enablerField`, etiqueta geométrica, cabecera de columna heredada, pulsadores fuera y `/Sig` con `FieldKind.SIGNATURE` · verde ✅ |
| 0.10.14 | Corrección de la 5·4b: el ancla ya no exige mayúsculas y se acota a 50 caracteres, no se emiten secciones vacías y se fusionan títulos repetidos seguidos. `CAMBIO TITULAR` baja de 20 campos a 7 y las 19 secciones quedan en 16 ⚠️ sin confirmar |
| 0.10.15 | Fase 5, tanda **5·4d** (1ª mitad): `ValueRouting.kt` — los valores se reparten entre el mapa de texto y el de botones según `FieldKind`, con el `onState` real del PDF ✅ |
| 0.10.16 | Tanda **5·4d** (2ª mitad): Relleno pinta casillas y radios como tales. `distinct()` en `fillSectionsFrom` — un grupo de 6 opciones pintaba 6 filas idénticas ✅ |
| 0.10.17 | Tanda **5·4e**: `FieldKeys.labelOf` consulta la etiqueta del esquema. La corrección del editor no llegaba a Relleno ✅ |
| 0.10.18 | Tanda **5·4f**: `SchemaEditing.setCanonical` + `CanonicalCatalog` + selector en el editor. Se pueden **asignar canónicas a mano** ⚠️ sin confirmar |
| 0.10.19 | Tanda **5·4g**: `CanonicalMapper` — la IA propone los enganches por texto, sin tocar el proxy (`task=extract` admite 0 imágenes). Botón «Asignar mis datos con IA» ❌ build rojo |
| 0.10.20 | Arreglo de los tres errores de la 0.10.19. Sin cambios de comportamiento · verde ✅ |
| 0.10.21 | Tanda **5·4h**: crash al bajar por la lista de Relleno (clave duplicada en `LazyColumn` + nombre repetido entre secciones), el aviso nombra los campos, y los huecos sin sugerencia van a un desplegable plegado ⚠️ sin confirmar |
| 0.10.22 | Tanda **5·4i, mitad 1** (lógica, Kotlin puro): `setCanonical` deja de ser exclusivo, `CanonicalSiblings` reparte un valor a los hermanos vacíos que comparten canónica, `AffinityGroup` detecta candidatos por etiqueta idéntica o canónica ya compartida (respeta `thirdParty`). `CanonicalMapper` se deja sin tocar a propósito (sigue 1:1). La mitad 2 (Compose, lista con casilla en Relleno) va en tanda aparte, sin typecheck local (no había `kotlinc` moderno disponible en la sesión — usa trailing commas, y el `kotlinc` 1.3.31 de apt no las soporta) · verde ✅ |
| 0.10.23 | Tanda **5·4i, mitad 2** (Compose): en `FieldRow` de `FillStep.kt`, cuando un campo tiene valor y `AffinityGroup` propone candidatos vacíos, aparece un botón plegable «Este dato aparece en otros N campos» con una casilla por candidato. Marcarla llama a `WizardViewModel.confirmAffinity()`: si el origen tiene canónica, se la asigna también al candidato (enganche real, `CanonicalSiblings` los mantiene sincronizados); si no, es copia puntual del valor. Deshacible con `setFieldValue` de por medio, sin typecheck local (mismo motivo que la 0.10.22) · verde ✅ (confirmada por Pablo en el móvil) |
| 0.10.24 | Tanda **5·4i**, arreglo tras la prueba en el móvil: un campo enganchado a mano en «Revisar mapeo» a la misma canónica que otro no se rellenaba al subir documentación —`FieldResolver.resolve` indexa por `keys.real(canonKey)`, un solo nombre por canónica, así que el hermano nunca entraba en `autoValues`. Se enchufa `CanonicalSiblings.expand` también tras la extracción por IA, y los hermanos nuevos heredan `FieldState`/`FieldOrigin` del campo que la IA sí decidió, para que no caigan al desplegable de «sin sugerencias» pese a tener valor ⚠️ sin confirmar, sin typecheck local |
| 0.10.25 | Tanda **5·4i**, dos arreglos más del mismo QA: (1) la lista de afines en Relleno se recalculaba con `state.fieldValues` entero, así que marcar UNA casilla recomponía y encogía la lista entera — ahora se congela por campo (`remember(key, value.isNotBlank(), schema)`) y cada casilla lleva su propio estado marcado, así que se pueden marcar varias sin que desaparezcan; de paso se quita un `clickable` duplicado que disparaba `confirmAffinity` dos veces por toque. (2) «Asignar mis datos con IA» (`CanonicalMapper`) dejaba sin vincular campos que claramente coincidían: `disponibles` excluía canónicas ya `ocupadas` y `sanitize()` descartaba duplicados — ambos frenos tenían sentido con `setCanonical` exclusivo (ya no lo es desde la mitad 1) y ahora sólo estorbaban. Se ofrece el catálogo completo y no se descartan duplicados; el riesgo titular/tercero se cierra en el prompt (regla 5 nueva), no en el filtro · verde ✅. Pendiente: QA de extremo a extremo con el 036 real y revisión de por qué «algunos etiquetan mal» — ninguno de los dos se puede hacer sin dispositivo, PDF de Aire real y acceso al proxy |
| 0.10.26 | Tanda **5·4j**, del QA con el contrato de Aire real y su PDF resultante: (1) **el etiquetado por visión iba desplazado** — `collectTargets` numeraba los tokens en orden de secciones del esquema, no en orden visual, y un modelo que tira de índice en vez de coordenadas desplazaba la página entera (`Email representante` → «NOMBRE O RAZÓN SOCIAL:», `TIF` → «Localidad:»). Ahora se ordenan por página y posición de lectura (tolerancia 12pt para no romper filas) ANTES de numerar, y el prompt pide explícitamente usar las coordenadas. (2) **los datos del cliente acababan en los bloques de terceros** — `FormField.thirdParty` existía pero NADIE lo ponía nunca a `true`, así que el CIF y el domicilio de MOFIZOL salieron impresos como los del «titular donante» y del «cambio de titular». `ThirdPartyDetector` (nuevo) marca la bandera por título de sección, y `CanonicalSiblings.expand` deja de cruzar esa frontera ⚠️ sin confirmar, sin typecheck local |
| 0.10.27 | Tanda **5·4k**, del reporte de que la 0.10.26 dejó el etiquetado peor: (1) el arreglo de la 5·4j sólo alineaba la PRIMERA tanda de cada página — los objetivos se parten en tandas de 24 y a cada una se le manda la página entera, así que con la numeración corrida la segunda tanda empezaba en `k24` y, siendo ya una banda contigua, un motor que empareje por índice le ponía los rótulos del principio de la página (medido: 66 de 136 objetivos desplazados en bloque). Ahora los tokens se numeran desde 0 en cada tanda, una tanda no parte una fila impresa, y la banda vertical viaja al prompt. (2) El orden usaba `(y/12f).toInt()`, el troceado en tramos fijos contra el que ya avisaba `PdfFieldInspector.orderByReadingRows`: el criterio pasa a `ReadingOrder` y lo usan los dos. (3) **«NIF: Off»**: `CHECKBOX_NIF` vale `"NIF"` y el contrato de Aire tiene un campo de TEXTO llamado igual; los mapas fijos se suman después de `routeFieldValues` y se lo saltaban, y `applyButtonValue` caía en su `else` escribiendo `Off` en un campo de texto — `ValueRouting.onlyButtons()` los filtra contra el esquema. Lógica nueva en Kotlin puro (`ReadingOrder`, `LabelTargetPlan`), typecheckeada y **ejecutada** en local: 56 casos en verde ⚠️ sin confirmar |

### Qué está enganchado y qué no

**Enganchado desde la 0.10.4**: Ajustes › Herramientas (beta) › «Analizar y etiquetar un PDF» →
SAF → `PdfFieldInspector.inspect()`/`pageCount()` → `TemplateFingerprint` → esquema guardado en
`schemas_v1` si ese PDF ya pasó, o `FormSchemaBuilder.build()` → `LabelEditor` → persistir. Con
su propio `LabelEditorViewModel` y su propia ruta `etiquetas` en el NavHost.

Desde la 0.10.5 ese mismo editor tiene un botón **«Etiquetar con IA»** que pasa `FieldLabeler`
sobre el esquema entero (`VisionLabelPass`), así que la fase 3 ya está enganchada.

**Enganchado desde la 0.10.10 (5·4)**: subir un PDF por el paso 1 ya inspecciona, calcula la
huella con el nº de páginas real, construye el `FormSchema` si no estaba y lo persiste. **`FillStep`**
se dibuja desde ese esquema, no desde los 21 campos de `CANON`.
`BuiltinSchemas.recognize(fieldNames)` reconoce Orange y el contrato de Aire por nombres
característicos.

⚠️ **Corrección a lo que dice el `CHANGELOG` de la 0.10.10**: ahí se afirma que `MappingEditor`
«se dibuja desde el `FormSchema`», y no es cierto. Lo que la 5·4 le añadió fue **filtrar las
opciones del desplegable** por `FieldKind` compatible; las **filas siguen siendo las 21 canónicas
de `ContractFields.CANON`**, o sea las de Orange, cargues el PDF que cargues. Se vio al implementar
la 0.10.12.

**Enganchado desde la 0.10.12**: «Revisar mapeo» en el paso 1 abre el **mismo panel que Ajustes**
(`SchemaReviewPanel`, extraído de `LabelEditorScreen` y compartido por los dos), sembrado con el
contrato ya elegido vía `LabelEditorViewModel.ensureLoaded(uri)`; al confirmar, `WizardViewModel`
lo adopta con `adoptSchema()`. Y el botón se ofrece **siempre** que haya un PDF propio con campos,
ya no depende de `needsMapping`.

Con eso, **`MappingEditor` ya no es alcanzable desde el asistente**. No está borrado y no se borra
(regla de abajo): sigue sirviendo para enlazar canónicas cuando el PDF es un contrato conocido, y
`TemplateMapper.suggest()` sigue alimentando `fieldMapping` al elegir contrato.

**Enganchado desde la 0.10.13/0.10.14 (5·4b)**: las secciones se llaman como en el papel
(`DATOS DEL CLIENTE`, `AIRE CONNECT`…) porque salen del texto del PDF vía `LayoutTextExtractor`,
y se delimitan **por intervalo entre anclas**, lo que además hizo desaparecer el fallo de orden
de la 5·4. Las casillas de banda son `FormSection.enablerField`. Los 3 pulsadores quedan fuera
del esquema y los 4 `/Sig` entran con `FieldKind.SIGNATURE`.

**Enganche de canónicas (0.10.18 y 0.10.19)**: hay tres vías y conviven — a mano con el selector
del editor, la heurística local `CanonicalCatalog.proposeFor()` (sugerencia en un chip), y la IA
con `CanonicalMapper` (botón «Asignar mis datos con IA», texto puro por `task=extract`).
`FieldLabeler` sigue devolviendo **sólo etiquetas**; el enganche es un paso aparte. Mientras un
campo no tenga `canonical`, siguen mudos el autorrelleno desde el perfil, la validación por tipo y
el teclado — están escritos y funcionan, sólo les falta el enganche.

**Enganchado desde la 0.10.15 (5·4d, 1ª mitad)**: `WizardViewModel` reparte los valores por
`FieldKind` antes de generar (`routeFieldValues`), así que un botón se escribe con
`applyButtonValue()` y con su estado real (`/Sí`, `/0`..`/5`, `/Opción1`) y no con
`setValue("On")`. Los `/Sig` no se escriben por ninguna vía.

**Enganchado desde la 0.10.16 (5·4d, 2ª mitad)** — *esto estuvo mal descrito aquí hasta la
0.10.21, que decía «NO enganchado» cuando llevaba cinco versiones hecho; comprobado en el código*:
`FillStep.kt` despacha por `FieldKind` (checkbox como casilla, grupo de radio como un solo
selector, `SIGNATURE` con `return`) y `FillSections.kt` filtra los `/Sig` del recuento de «faltan
N campos».

**Enganchado desde la 0.10.21 (5·4h)**: la lista de Relleno ya no crashea al bajar —clave de
`LazyColumn` con índice delante y un nombre no se pinta dos veces aunque aparezca en dos
secciones—, el aviso de decisiones nombra los campos, y los huecos sin nada se apartan a un
desplegable plegado al final.

**Enganchado desde la 0.10.22 (5·4i, mitad 1)**: `setCanonical` permite N campos por canónica,
`CanonicalSiblings` reparte el valor a los hermanos vacíos al escribir en Relleno (vía
`pushUndo`).

**Enganchado desde la 0.10.23 (5·4i, mitad 2)**: `FieldRow` (`FillStep.kt`) llama a
`AffinityGroup.candidatesFor` y, si hay candidatos, pinta el botón plegable con casilla por
campo. Marcarla llama a `WizardViewModel.confirmAffinity()`. **Confirmada por Pablo en el móvil**
(capturas: «Localidad» con 3 casillas de afines).

**Enganchado desde la 0.10.24**: la extracción por IA (`runExtraction()`) también reparte por
`CanonicalSiblings.expand` tras `FieldResolver.resolve()`, así que un campo enganchado a mano a
la misma canónica que otro sí se autorrellena ahora al subir documentación. **Sin probar en el
móvil todavía.**

**Enganchado desde la 0.10.25**: la lista de afines en Relleno ya no se encoge al marcar una
casilla (congelada por campo, estado marcado por candidato). «Asignar mis datos con IA»
(`CanonicalMapper`) ya ofrece el catálogo completo y no descarta duplicados, así que puede
enganchar la misma canónica a varios campos coincidentes de una vez. **Sin probar en el móvil
todavía — ninguno de los dos.**

Aparte, y sin relación con `AffinityGroup`: `LabelEditor.kt` → `CanonicalPicker` deja elegir
CUALQUIER entrada de `CanonicalCatalog.ALL` en cada campo, sin excluir las ya usadas por otro —
antes esto era inofensivo porque `setCanonical` deshacía la del campo anterior en cuanto elegías
la misma en uno nuevo; ahora que no lo hace, **abrir el editor de etiquetas y asignar la misma
canónica a mano en dos campos también funciona**, sin pasar por Relleno.

**Enganchado desde la 0.10.26 (5·4j)**: los objetivos del etiquetado por visión se numeran en
orden de lectura (`VisionLabelPass.collectTargets`), y `ThirdPartyDetector.mark()` corre justo
después de `SchemaLabeling.apply()` marcando `thirdParty` en las secciones de tercero, que
`CanonicalSiblings` ya no cruza. **Sin probar en el móvil todavía.**

**Enganchado desde la 0.10.27 (5·4k)**: `VisionLabelPass` ya no decide por su cuenta qué
preguntar — lo hace `LabelTargetPlan` (numeración por tanda, filas sin partir, banda al prompt),
y el orden de lectura es uno solo (`ReadingOrder`, por ancla de fila y no por tramos fijos).
Los mapas de casillas fijos pasan por `onlyButtons()` antes de llegar a `AcroFormFiller`.
**Sin probar en el móvil todavía.**

**NO enganchado**: la fase 6 (`/Sig`) y las tablas del Relleno (5·5).

---

## 4. Lo que toca

Por orden. La primera no es código.

- **Probar el etiquetado en el móvil con un PDF real de Aire.** La 0.10.5 está verificada por
  compilación y por prueba de comportamiento, pero nadie ha visto todavía qué etiquetas devuelve
  la visión sobre el contrato de 481 campos. Si salen mal, el sitio a ajustar es el prompt de
  `FieldLabeler.buildPrompt()` y el ancho de render de `VisionLabelPass` (1400 px).
- **Unificar las tres aperturas del PDF** en `LabelEditorViewModel.pickPdf()`: hoy abre el
  documento tres veces (campos, nº de páginas, nombres). Pide una API del inspector que devuelva
  las tres cosas de una pasada.
- ~~**Arreglos de normalización y procedencia**~~ ✅ *hechos en la `0.10.11`*: la inversión del
  nombre por la última coma y el domicilio de los documentos de identidad. Los otros dos que se
  habían apuntado **no hacían falta**, comprobado leyendo el código: el firmante del 036 ya estaba
  cubierto (`RISKY_SOURCES` marca `REPRESENTANTE_NOMBRE`/`_NIF` desde un `Modelo 036` como dudosos
  desde antes), y **el merge sí deduplica por valor** — `FieldResolver` enriquece el candidato que
  ya existe en vez de añadirlo, y `decide()` vuelve a aplicar `distinctBy` antes de declarar
  conflicto. Lo que hay que verificar en dispositivo es que esa protección del 036 **se dispara**,
  no añadirla.

- **Fase 5 — relleno dinámico**: es la que hace que un PDF de Aire se rellene de punta a punta, y
  la que toca `WizardViewModel`. **No la ataques de una vez: está partida en tandas en
  `docs/PLAN_FASE_5.md`.** Hechas: 5·0 y 5·1 (0.10.6), 5·2 (0.10.7), 5·2b (0.10.8), **5·3
  (0.10.9), la de riesgo alto**, y **5·4 (0.10.10)**.

  **La 5·4b está hecha** (0.10.13 y 0.10.14): títulos de sección del texto del PDF, secciones
  por intervalo entre anclas —lo que además hizo desaparecer el fallo de orden que dejó la 5·4—,
  `enablerField` por banda, etiqueta geométrica antes que la IA (74% de los sueltos con cero
  llamadas, medido), cabecera de columna heredada por las celdas, pulsadores fuera del esquema y
  `/Sig` con tipo propio. Plan en `docs/PLAN_ETIQUETADO_ORGANICO.md`.

  **La 5·4c** (agrupar por sección en el orden del papel) salió gratis con el seccionado por
  intervalo: verificado simulando el algoritmo sobre el contrato real.

  **La 5·4d está entera** (0.10.15 la 1ª mitad, 0.10.16 la 2ª): el valor se reparte por
  `FieldKind` al generar y la pantalla pinta casilla como casilla, grupo de radio como un solo
  selector y `/Sig` fuera del recuento. Este documento afirmó lo contrario hasta la 0.10.21; si
  vuelves a leer que la 2ª mitad está pendiente, mira `FillStep.kt` antes de reescribirla.

  **Las 5·4e a 5·4h están hechas** (0.10.17 etiquetas en Relleno, 0.10.18 canónicas a mano,
  0.10.19/0.10.20 canónicas por IA, 0.10.21 la lista usable con 461 campos).

  **La siguiente decisión es de Pablo, no de código**: probar en el móvil de la 0.10.16 a la
  0.10.21 y decir qué falla. Ver §5.

  **El mapeo se conserva**: `MappingEditor` y `TemplateMapper.suggest()` no se retiran.

- **Tanda 5·5 — tablas en el Relleno**: filas dinámicas, catálogo local (lleva comisiones: no sale
  del dispositivo) y `cuota total = cantidad × cuota unitaria`. No bloquea el alta, porque el alta
  usa páginas 1 y 3 y ahí no hay tablas.
- **Fase 6 — firma**: manejar los campos `/Sig` del AcroForm (4 en el contrato de empresas, 1 en
  la página 2 y 3 en la 3; 2 en portabilidad), no sólo estampar imagen en coordenadas.
  ⚠️ **Esto ya no es sólo «lo siguiente»: hoy está roto para Aire.** Con el contrato de empresas
  cargado, el paso 1 dice «0 huecos de firma» (visto en una captura de la 0.10.11), porque
  `SignaturePageDetector` los busca por geometría y no mira el AcroForm. Sin huecos no hay dónde
  estampar, así que **un alta de Aire hoy no se puede firmar en la app**. Es lo que hay que decidir
  si adelanta a la 5·5.

- ~~**Deuda menor**: los siete campos fantasma del esquema~~ ✅ *hecha en la 0.10.13*: los 3
  pulsadores (`Ff = 65536`) quedan fuera del esquema y los 4 `/Sig` entran con
  `FieldKind.SIGNATURE`. **Decisión tomada y razonada**: no se sacan del esquema, porque hoy es
  el único sitio donde esos cuatro campos existen —`SignaturePageDetector` no mira el AcroForm—
  y sacarlos le quitaría a la fase 6 exactamente lo que necesita. Con tipo propio se resuelven
  las dos cosas: la fase 6 los encuentra y el usuario no puede escribir dentro.

---

## 5. Pendiente de verificar (no lo des por bueno)

- **La 0.10.21 está sin confirmar en Actions.** Pregunta el resultado antes de abrir tanda nueva.
  La 0.10.20 sí está verde (run 33724263803).
- **Nada de la 0.10.16 a la 0.10.21 se ha probado del todo en el móvil.** Lo que hay que mirar con
  el contrato de Aire: que las casillas y radios salgan como controles y no como cajas de texto
  (0.10.16), que Relleno muestre la etiqueta corregida y no `Casilla de verificación 59` (0.10.17),
  que el chip de canónica y su sugerencia aparezcan en los campos de texto del editor (0.10.18) y
  que «Asignar mis datos con IA» devuelva enganches razonables (0.10.19).
- **De la 0.10.21, lo que hay que mirar es exactamente lo que la rompió**: que **bajar por la lista
  de Relleno ya no crashee** (era `Key was already used`, y saltaba al hacer scroll, no al cargar),
  que el aviso de decisiones **diga qué campos** son, y que el desplegable «Sin sugerencias» se
  abra y deje rellenar a mano. Con el contrato de Aire eran 461 huecos y 14 rellenos por la IA.
  Y una que no se ve pero importa: que ningún campo aparezca **dos veces** en secciones distintas
  editando el mismo valor.
- **De la 0.10.14 y la 0.10.15 (histórico).** Pregunta el resultado antes de
  abrir tanda nueva.
- **De la 0.10.14, en el móvil hay que mirar**: que `CAMBIO TITULAR` tenga **7 campos y no 20**,
  que aparezca una sección aparte con la fecha y las dos firmas (los 9 campos del alta de la
  página 3), y que no salga **ninguna cabecera vacía** ni `AIRE CONNECT` **dos veces seguidas**.
- **De la 0.10.15 no se ve nada en pantalla todavía** —el reparto por tipo sólo se nota al
  generar—, y no se notará hasta la 2ª mitad, que es la que pinta casillas. Lo que sí se puede
  comprobar ya: que **el contrato de Orange genera exactamente el mismo PDF** que antes.
- **La 0.10.12 está verde pero no se pudo typecheckear**, por ser Compose. Hay dos cosas suyas que
  sólo se ven en el móvil: que el panel de revisión del paso 1 sale con las secciones del PDF (y no con las
  21 canónicas de Orange), y que «Etiquetar con IA» funciona ahí igual que en Ajustes.
- **«0 huecos de firma» con el contrato de Aire**, visto en una captura de la 0.10.11.
  `SignaturePageDetector` busca huecos por geometría y no mira los campos `/Sig` del AcroForm, que
  en ese contrato son cuatro. Es la fase 6; queda anotado para que no se diagnostique dos veces.
- **La 0.10.10 (tanda 5·4) está verde pero sin probar en dispositivo.** Tres cosas: que con el
  contrato de Orange salga **exactamente lo mismo** que antes (regla del roadmap HTML); que el
  contrato de Aire abra desde el paso 1 sin haber pasado antes por Ajustes › «Analizar y
  etiquetar»; y que en el PDF final `MODIFICACIÓN` y `PORTABILIDAD` salgan **sin marcar** (las
  tres casillas de cabecera vienen de fábrica con `/V = /Sí`).
- **Siete campos fantasma en el esquema del contrato de Aire.** `PdfFieldInspector` marca
  `isCheckbox`/`isRadio` y todo lo demás cae en el `else -> FieldKind.TEXT` de
  `FormSchemaBuilder.toField()`. Eso mete como campos de texto rellenables los **3 pulsadores**
  (`Botón 2`/`3`/`4`, `Ff = 65536`, los enlaces «descargar aquí») y los **4 campos `/Sig`**
  (`Signature1`..`Signature4`). Comprobado con `pypdf` sobre `Contrato_empresas.pdf`. Se arregla
  en la 5·4b (ver `docs/PLAN_ETIQUETADO_ORGANICO.md` §5).

### Lo que destapó un juego de documentación real (2026-09-03)

Probado el flujo con un juego real de tres documentos de un alta —DNI por las dos caras, tarjeta
del NIF de la AEAT y Modelo 036 completo—. **Los ficheros no están en el repo** (llevan datos
personales); lo que sigue son los patrones, que sí conviene tener escritos.

- **La ruta de extracción hay que decidirla por documento, no por sesión.** Medido: el Modelo 036
  traía **41.769 caracteres de capa de texto** en 11 páginas, mientras que la tarjeta del NIF
  (escaneada con CamScanner) y el DNI daban **cero**. Mandar el 036 como 11 imágenes cuando el
  texto está encima es caro y peor; insistir en extraer texto de un escaneo devuelve la cadena
  vacía y parece que el documento está roto.
- **De las 11 páginas del 036, sólo 3 sirven.** Las páginas 2, 3 y 4 (datos identificativos,
  identificación con domicilios y teléfonos, y representantes) suman 9.920 de esos 41.769
  caracteres — un 24%. El resto es IVA, IRPF, retenciones y actividades. Con
  `DocumentTypeDetector` ya identificando el modelo, recortar ahorra el 76% del contexto y quita
  ruido.
- ~~**La dirección del DNI no es el domicilio del cliente.**~~ ✅ *arreglado en la `0.10.11`.* El reverso del DNI trae el domicilio
  particular del representante; el de la empresa está en el 036 y en la tarjeta del NIF. En el
  caso probado las dos estaban en el mismo municipio y la misma provincia, así que **la validación
  CP↔provincia da verde con la equivocada**. Sólo la política de procedencia lo puede parar, y hoy
  `AutoFillPolicy.RISKY_SOURCES` ya contempla el documento de identidad (`ID_DOCS`). **Verificar
  en dispositivo** que una dirección que sólo salga del DNI aparece «por decidir», y que con un
  censal o un 036 en el lote no cambia nada.
- **El firmante del 036 era la gestoría**, no el representante: el pie de la página 1 traía una
  razón social de un organismo y «En calidad de: Funcionario Público Habilitado» (alta tramitada
  por un PAE). El representante real estaba en la casilla 305 de la página 3. Es exactamente el
  caso que la 0.10.9 dice haber reactivado y que sigue sin comprobarse en dispositivo.
- ~~**La inversión de nombre no se aplica con tres trozos.**~~ ✅ *arreglado en la `0.10.11`.* El
  036 escribe el representante como `APELLIDO1, APELLIDO2, NOMBRE` y la regla de la 0.10.8 exigía
  exactamente dos trozos, así que el valor **salía tal cual al PDF**, con comas y en orden
  apellidos-primero. (Corrección a lo que decía antes esta línea: la guarda `parts.size == 2`
  impedía que se produjera un nombre inventado; el fallo era por omisión, no por invención.)
  **Verificar en el PDF final** que ahora entra como «Nombre Apellido1 Apellido2».
- **Dos formas de la misma razón social** (la tarjeta del NIF añade `(EN CONSTITUCIÓN)`). No es un
  conflicto que preguntarle al usuario: es normalización.
- **Un NIF provisional valida igual.** La tarjeta lo dice expresamente y el dígito de control es
  correcto, así que `SpanishValidators` no dirá nada. Merece un aviso, no un bloqueo: Aire puede
  rechazar el alta.
- **Un teléfono y un correo para tres campos.** El 036 los declara como datos de contacto para
  avisos de la AEAT **de la entidad**; el contrato tiene `Tfno.` del cliente, `Móvil
  Representante` y `E-mail Representante` por separado. Autorrellenar los tres con lo mismo es
  plausible pero no está escrito: deben salir «por decidir».
- **Tres candidatos idénticos no son un conflicto** — y esto **ya está bien resuelto**, revisado
  al implementar la 0.10.11. El 036 repite el bloque de local una vez por epígrafe IAE (tres, en el
  caso probado) con la misma dirección; `FieldResolver` enriquece el candidato existente en vez de
  añadirlo y `decide()` aplica `distinctBy` por valor antes de declarar conflicto. Se deja escrito
  para que nadie lo «arregle» dos veces.
- **Cobertura real del alta con esos tres documentos**: de los 15 campos de DATOS DEL CLIENTE, 8
  autorrellenados con certeza, 3 «por decidir» y 4 vacíos por falta de dato (fax, contacto de
  administración, TIF y correo de administración). Los 8 del bloque DISTRIBUIDOR y los 2 del
  comercial de la página 3 **no salen ni pueden salir de la documentación del cliente**: son fijos
  de Pablo y deben ir a Ajustes con `ValueOrigin.AJUSTES`, igual que el responsable de Orange.
  Sin eso, un alta de Aire sale con la cabecera del distribuidor en blanco.

- **Dos campos de Orange empiezan a validar en la 0.10.7 (tanda 5·2) y antes no lo hacían.**
  `FieldValidator` decidía por `base(fieldName) == "algo"` sobre el nombre normalizado; con
  nombres de dos palabras eso se rompía por un espacio que la comparación no esperaba:
  `"NIF representante"` normaliza a `"nif representante"` (con espacio) pero se comparaba contra
  `"nifrepresentante"` (sin espacio) — nunca casaba. Lo mismo con `"Datos bancarios del
  DISTRIBUIDOR"` contra `"datosbancarios"`. Confirmado por simulación en Python del dispatch
  viejo/nuevo sobre los 21 campos de `CANON`: son los únicos 2 que cambian, los otros 19 dan
  igual. Con la canónica (que compara la clave completa, no un fragmento del nombre) sí validan:
  NIF/NIE del representante y IBAN mod-97. **No es una regresión de la 0.10.7, es un fallo previo
  que queda expuesto** — misma clase de caso que el de las casillas, justo abajo. Verificar en
  dispositivo metiendo un NIF de representante o un IBAN inválidos en el contrato de Orange:
  ahora deben salir en rojo.
- **La protección contra datos de terceros estaba apagada con PDFs propios, y la 0.10.9 la
  reactiva.** `AutoFillPolicy.RISKY_SOURCES` estaba indexado por los nombres de campo de Orange,
  así que con un PDF de Aire ninguna clave casaba: un IBAN sacado de un «Contrato de alquiler»
  —que es el del arrendador, no el del distribuidor— se marcaba como AI y se autorrellenaba en
  vez de pedir confirmación (WARN). Lo mismo con los emails y el teléfono de un alquiler, y con
  el representante de un Modelo 036 (que suele firmar la gestoría). **Verificar** subiendo un
  contrato de alquiler junto al resto y comprobando que el IBAN sale marcado «por decidir» y no
  autorrellenado. En Orange esto ya funcionaba y debe seguir igual.
- **La 0.10.9 (tanda 5·3) cambia la clave de todos los valores: hay que probar los DOS
  contratos.** En Orange no debe cambiar nada en absoluto (ahí `FieldKeys` es la identidad, y así
  está verificado); si algo cambia, es un fallo de esta tanda. Con un PDF propio **debe mejorar**:
  el paso de Relleno tiene que mostrar ya los campos que la IA extrajo, que hasta ahora salían en
  blanco. Comprobar además:
  · que una **sesión guardada antes de actualizar** se restaura sin perder valores (la migración
    v1→v2 es idempotente y con mapeo vacío no toca nada, pero es la parte que ya dolió en la 0.8.0);
  · que RELLENO **no** se reabre en DOCUMENTOS al restaurar — el umbral de `migrateStepIndex` se
    dejó en `>= 1` a propósito y no en `SCHEMA_VERSION`, que ahora vale 2;
  · que un **perfil del historial** guardado antes se aplica bien (se migra al leerlo);
  · que un formulario **sin** campo de fecha ya no reporta «falta un campo».
- **El IBAN se escribe compactado desde la 0.10.8 (tanda 5·2b), y antes no.** Mismo bug de
  espacios que el punto anterior, pero en `FieldNormalizer.normVal`: `"Datos bancarios del
  DISTRIBUIDOR"` nunca casaba con `"datosbancarios"`, así que el valor que devolvía la IA iba al
  PDF tal cual, con sus espacios. Ahora se compacta, que es lo que hace la app web (paridad).
  Por lo mismo, el NIF del representante se pasa a mayúsculas y se le quitan puntos/guiones, y un
  «Apellidos, Nombre» se invierte a «Nombre Apellidos». **Verificar en el PDF final** que el IBAN
  sale sin espacios y que el nombre del representante no se ha dado la vuelta cuando no debía.
- **Casillas del contrato de Orange.** La 0.9.7 cambió el estado de activación de `/On` al real
  del PDF. Si `/On` tampoco encajaba antes, las CIF/NIF/NIE llevarían sin marcarse desde siempre
  sin que nadie lo supiera (el fallo era silencioso). Si ahora se marcan, **no es una regresión**.
  Confirmar contra un contrato firmado real.
- **Totales de columna** de las tablas de tarifa. La regla por fila está confirmada en 21 filas
  del tarifario; el sumatorio de la fila TOTAL se dedujo de una captura de baja resolución.
- **`Conectividad.pdf` tiene un defecto de origen**: filas 07 y 08 superpuestas en la misma
  coordenada (y = 271,02). `FormSchemaBuilder` lo refleja en vez de taparlo, a propósito.
  Decidir si se apaña por software o se corrige el PDF en Aire.
- **Paridad con la web**: dos cambios de prompt sin replicar en `rellenador-pro.html`
  (`tipo_documento` de la 0.7.9 y la genericización del operador de la 0.9.5).
- **Deuda (sigue abierta en la 0.10.5)**: `FieldLabeler` usa `task = "locate_signature"` porque es
  la única tarea de visión que expone el proxy. Funciona, pero el nombre engaña a quien lea el
  código o el log del servidor. Arreglarlo es tocar `ai-proxy.php`, que se despliega por
  FTP/cPanel y no vive en este repo.
- **El auto-mapeo puede asignar un checkbox a un campo de texto.** Visto en una captura con el
  contrato de Aire cargado: `Fecha · mes` → `Casilla de verificación 56`. No se arregla en el mapeo
  viejo (la 5·4 lo sustituye), pero el nuevo debe comprobar que el `FieldKind` del destino es
  compatible con el del origen. Ver `docs/PLAN_FASE_5.md` §6.5.

---

## 6. Reglas de trabajo


- **En `LazyColumn`, la clave tiene que ser ÚNICA de verdad.** `key = { it.title }` sobre las
  secciones de un esquema aprendido crasheaba con `Key was already used` **al hacer scroll**, no
  al cargar, porque el fallo salta cuando el duplicado entra en composición. Si la clave sale de
  datos del PDF, mete el índice delante. Lo mismo vale para `rememberSaveable(key)`.
- **Un parámetro nuevo en un `@Composable` va ANTES de la lambda trailing.** Añadirlo al final
  rompe en silencio a los llamadores que pasan la lambda suelta (`Foo(x) { … }`): esa lambda
  enlaza con el parámetro nuevo. El compilador señala el llamador, no la firma. Build rojo de la
  0.10.19.
- **El CI NO compila `app/src/test`.** `.github/workflows/android.yml` sólo corre
  `./gradlew assembleDebug`. `CanonicalAssignmentTest.kt` llevaba desde la 0.10.22 con un test
  llamado `` `desde la 5:4i …` `` —los dos puntos son un carácter ilegal en un nombre de método
  de la JVM, `kotlinc` lo rechaza— y todos los builds salieron verdes igual. O sea que **ninguna
  de las «comprobaciones ejecutables» del `CHANGELOG` la ha ejecutado nunca el CI**: sólo las
  sesiones que tenían un `kotlinc` a mano. El nombre se arregló en la 0.10.27; añadir un paso
  `./gradlew testDebugUnitTest` al workflow es decisión de Pablo.
- **El orden de lectura de una página se pide a `ReadingOrder`, no se reimplementa.** Trocear el
  eje Y en tramos fijos (`(y / TOL).toInt()`) parte una fila impresa en cuanto el corte cae entre
  dos de sus campos. Ya pasó en el SEPA (fila del BIC) y se volvió a colar en `VisionLabelPass`
  en la 0.10.26. Se agrupa por ancla de fila.
- **Lo que se le manda a un motor de visión se manda por tandas, y la tanda es el contexto.** No
  basta con ordenar bien los objetivos de la página: si se pregunta en varias llamadas, cada una
  tiene que numerar desde 0 y decir qué banda cubre, porque el motor no ve las otras tandas y
  puede emparejar por índice.
- **`ProxyResponse.text` es `String?`.** Un motor puede responder `ok` sin cuerpo; todo lo que
  parsee la respuesta del proxy pasa por `?.let`.
- **Una tanda, una versión, un build verde** antes de la siguiente. Subir `versionCode` y
  `versionName` siempre, y añadir la entrada al `CHANGELOG.md`. Un hotfix sobre una versión que
  nunca llegó a verde NO incrementa.
- **Aquí no se compila Android.** No hay SDK y la red no llega a `dl.google.com` ni a Maven
  Central. El juez del build es GitHub Actions, con el workflow `.github/workflows/android.yml`
  (de Pablo — no tocarlo sin pedirle el contenido actual). El APK sale como artefacto
  `rellenador-<versionName>`.
- **Pero el Kotlin puro SÍ se puede typecheckear en local, y merece la pena.** `github.com` está
  permitido, así que el compilador se baja de las releases de JetBrains:

  ```
  curl -sSLO https://github.com/JetBrains/kotlin/releases/download/v2.1.0/kotlin-compiler-2.1.0.zip
  unzip -q kotlin-compiler-2.1.0.zip   # queda en ./kotlinc/bin
  ```

  Con eso se comprueba de verdad todo lo que no dependa de Android ni de pdfbox: el paquete
  `data/model` completo compila con cuatro stubs de una línea (`@Serializable`/`@SerialName` de
  `kotlinx.serialization`, `android.net.Uri`, `android.graphics.Color.rgb` y el enum
  `SignatureProcessor.Background`). Los stubs no alteran la comprobación de tipos del código
  real. Y cualquier patrón dudoso —inferencia genérica, desestructuración— se aísla en un fichero
  suelto con los mismos tipos y se compila con `-Werror` en segundos. Es mucho mejor que subir a
  ver qué dice el CI: dos builds se rompieron por no hacer justo esto.
- **El APK no puede ir «en el mismo commit».** Es la salida del CI *después* del push, por
  definición. Y commitear un zip con los fuentes no compila nada: Gradle compila el árbol de
  fuentes, así que un commit-zip da un build verde del código viejo.
- **No te quedes esperando el CI.** Pablo avisa del resultado.
- **Verifica antes de subir, no después.** Dos builds se rompieron por no hacerlo: un método de
  librería inventado de memoria (comprobar el fuente real de la dependencia) y un nombre de clase
  que ya existía en otro paquete (un `grep` de colisiones lo habría visto). Comprobar también los
  *call sites* de cualquier firma que cambies.
- **Formato de commit**: título = `rellenador-<versionName>` y nada más — así coinciden commit,
  título del run, zip del artefacto y APK. Cuerpo corto (qué y por qué), sin acentos; el
  razonamiento largo va al `CHANGELOG.md`.
- **Nunca asumir el estado del repo.** Clonar y mirar `git log`.
- **`AcroFormFiller.generate()` tiene dos mapas y no son intercambiables.** `values` se aplica
  con `field.setValue(String)`; `checkboxes` con `applyButtonValue()`, que es el único que sabe de
  `check()`/`unCheck()` y de los estados de activación reales. Cualquier casilla o radio va por el
  segundo, y con el `onState` que el esquema leyó del `/AP /N` — nunca con `"On"`. Aire usa `/Sí`,
  `/0`..`/5` y `/Opción1`, sin convención: escribir el estado equivocado **no lanza excepción**, se
  ve al abrir el PDF generado. El reparto está centralizado en `data/model/ValueRouting.kt`
  (0.10.15); si añades un `FieldKind`, ése es el sitio.
- **`"0"` no es «apagado».** Es un estado real de una banda del contrato de Aire. Apagado es
  cadena vacía o literalmente `Off`.
- Los nombres de campo del AcroForm son **exactos**: dobles espacios (`Nombre  Razón Social`),
  sufijos `_2`, y una casilla llamada literalmente `undefined`. No se normalizan nunca.
- **No borrar el camino del contrato de Orange.** Oculto en la interfaz
  (`SHOW_LEGACY_DEFAULT_CONTRACT = false`), pero debe seguir rellenándose si se **sube** como PDF
  propio. Se reconoce por huella.
- El **tarifario lleva las comisiones del distribuidor**: si el catálogo entra en la app, se queda
  **local**. No viaja al proxy ni a ningún motor de IA.
- Las **claves de las IAs no van en el binario**: la app llama a `ai-proxy.php`
  (`datingtrck.com/pdf/`), que no vive en este repo.

---

## 7. Acceso al repo — sin tokens en el chat

**No pegues un PAT en la conversación.** Queda en texto plano en el historial, se sincroniza con
la cuenta y no se puede borrar de ahí después; hay que revocarlo. Ya ha pasado dos veces.

Opciones, de mejor a peor:

1. **La integración de GitHub de Claude**, si está conectada: lee y escribe en el repo sin que le
   pases ningún secreto. Limitación real: para escribir tiene que **reenviar el fichero completo**,
   y `CHANGELOG.md` son 160 KB — reescribirlo entero para añadir 20 líneas arriba es arriesgado.
   Sirve de sobra para ficheros pequeños.
2. **Parche**: que Claude genere un `git format-patch` y lo apliques tú con
   `git am fichero.patch && git push`. Preserva el commit exacto, autor y mensaje incluidos, y
   pesa el delta y no el árbol. Es la vía recomendada para tandas grandes.
3. **Zip** con las rutas relativas, que extraes con `unzip -o` en la raíz y commiteas tú. Funciona,
   pero el mensaje de commit lo escribes a mano.
4. **PAT en el chat**: solo si no hay alternativa, y **revocándolo al terminar**.
