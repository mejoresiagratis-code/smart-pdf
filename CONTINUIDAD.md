# Continuidad — Rellenador PDF (Android)

> **Para qué es esto.** Arranque de sesión nueva con Claude. Pégale el enlace de este fichero
> o su contenido; con eso y el repo tiene el contexto completo. **No hace falta reenviar los
> PDFs de Aire**: su análisis está en `docs/ANALISIS_FORMULARIOS_AIRE.md`.
>
> **Actualizado**: 2026-09-03. La **0.10.10 salió verde**; es el último commit de código.
> Por encima hay commits sólo de documentación, que no suben versión. Este documento **caduca**: la primera regla de abajo
> existe precisamente porque lo que aquí se afirma puede estar viejo.

Habla en **español de España**.

---

## 1. Lo primero, siempre

Clona el repo y **verifica el estado real por `git log`. No te fíes de ningún documento,
incluido este.**

```
git clone https://github.com/mejoresiagratis-code/smart-pdf
cd smart-pdf && git log --oneline -8
```

El último commit **de código** debería ser `0.10.10-fase5-esquema` · versionCode 80.
Por encima puede haber commits solo de documentación, que no suben versión. Si el último código
no es ése, este documento está desfasado: manda `git log` y la cabecera del `CHANGELOG.md`.

### Estado del build

De la 0.10.6 a la **0.10.10, todas verdes** (comprobado en la API de Actions: el run de
`ea02fc7` concluye `success`). No hay nada pendiente de verificar en Actions.

Lo siguiente **no es código**: es probar en el móvil lo que ya está subido. Ver sección 5.

---

## 2. Qué leer, en este orden

1. `ROADMAP.md` — plan multi-formulario, decisión de arquitectura de expediente, fases.
2. `docs/ANALISIS_FORMULARIOS_AIRE.md` — análisis de los PDFs de Aire, verificado contra los
   ficheros reales. Es el brief técnico de las fases que quedan.
3. `docs/PLAN_FASE_5.md` — plan de la fase 5 partido en tandas, escrito leyendo el código.
   **De lectura obligada antes de tocar el asistente.** Su §6 tiene los requisitos acordados para
   la 5·4 (secciones en el orden del PDF, alcance del alta, casilla de ALTA), ya ejecutados.
3b. `docs/PLAN_ETIQUETADO_ORGANICO.md` — plan de la **5·4b**, la tanda que viene después de la
   5·4: que las secciones y los campos se llamen como en el papel. Escrito midiendo sobre el
   contrato con `pypdf`/`pdfplumber`; incluye el fallo de orden que dejó la 5·4.
4. `docs/roadmap-multiformulario.html` — el roadmap de las 7 fases con el **estado real** de cada
   una, qué falta y por qué. Ábrelo en el navegador; la primera pestaña es el resumen.
5. `CHANGELOG.md`, entradas **0.9.4 a 0.10.9**.
6. `docs/ESTADO_Y_GUIA_DE_CONTINUIDAD.md` — arquitectura e historia. **Ojo**: su bloque
   «ESTADO ACTUAL» se quedó en `v0.7.10` / versionCode 48 (agosto de 2026), 26 versiones por
   detrás. La parte de arquitectura y la de «archivo histórico» siguen siendo útiles; el estado
   por versión está en el `CHANGELOG.md`, que es la fuente de verdad.

---

## 3. Dónde estamos

Pablo ya no trabaja con Orange/MASORANGE. La empresa nueva es **Aire Networks** (airetech.es),
con cuatro formularios rellenables propios: contrato de empresas (481 campos), portabilidad fija
(202), conectividad (141) y mandato SEPA (19).

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

### Qué está enganchado y qué no

**Enganchado desde la 0.10.4**: Ajustes › Herramientas (beta) › «Analizar y etiquetar un PDF» →
SAF → `PdfFieldInspector.inspect()`/`pageCount()` → `TemplateFingerprint` → esquema guardado en
`schemas_v1` si ese PDF ya pasó, o `FormSchemaBuilder.build()` → `LabelEditor` → persistir. Con
su propio `LabelEditorViewModel` y su propia ruta `etiquetas` en el NavHost.

Desde la 0.10.5 ese mismo editor tiene un botón **«Etiquetar con IA»** que pasa `FieldLabeler`
sobre el esquema entero (`VisionLabelPass`), así que la fase 3 ya está enganchada.

**Enganchado desde la 0.10.10 (5·4)**: subir un PDF por el paso 1 ya inspecciona, calcula la
huella con el nº de páginas real, construye el `FormSchema` si no estaba y lo persiste. `FillStep`
y `MappingEditor` se dibujan desde ese esquema, no desde los 21 campos de `CANON`.
`BuiltinSchemas.recognize(fieldNames)` reconoce Orange y el contrato de Aire por nombres
característicos.

**NO enganchado**: la fase 6 (`/Sig`) y las tablas del Relleno (5·5). Y las secciones que sí se
dibujan **se siguen llamando «Página 1» y «Tabla 3»** — eso es lo que arregla la 5·4b, ver
`docs/PLAN_ETIQUETADO_ORGANICO.md`.

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
- **Arreglos de normalización y procedencia** (los del bloque de la documentación real, §5). Son
  pequeños, no tocan el asistente y se verifican en local con un juego de documentos de verdad.
  Van primero porque cada uno mete un dato equivocado en el PDF final:
    · la inversión «Apellidos, Nombre» parte por la **primera** coma y con tres campos
      (`APELLIDO1, APELLIDO2, NOMBRE`, que es lo que da el Modelo 036) produce un nombre
      inventado — debe partir por la **última**;
    · `AutoFillPolicy` no contempla el documento de identidad como fuente de riesgo: de un DNI
      sólo deben aceptarse nombre y NIF del representante, nunca el domicilio (que es el
      particular de la persona, no el de la empresa);
    · el firmante de un Modelo 036 suele ser la gestoría o un PAE, no el representante — el
      representante real está en la casilla 305 de la página 3;
    · el merge debe **deduplicar por valor** antes de declarar conflicto.

- **Fase 5 — relleno dinámico**: es la que hace que un PDF de Aire se rellene de punta a punta, y
  la que toca `WizardViewModel`. **No la ataques de una vez: está partida en tandas en
  `docs/PLAN_FASE_5.md`.** Hechas: 5·0 y 5·1 (0.10.6), 5·2 (0.10.7), 5·2b (0.10.8), **5·3
  (0.10.9), la de riesgo alto**, y **5·4 (0.10.10)**.

  **La siguiente es la 5·4b — etiquetado orgánico**, plan completo en
  `docs/PLAN_ETIQUETADO_ORGANICO.md`. La 5·4 consiguió que las secciones salgan del `FormSchema`;
  la 5·4b consigue que **se llamen como en el papel**, que es lo que hace usable el mapeo con 481
  campos delante. En corto:
    · los títulos de sección salen del texto del PDF (`DATOS DEL CLIENTE`, `AIRE CONNECT`,
      `PORTABILIDAD TELEFONÍA FIJA`…) en vez de «Página 1» y «Tabla 3»;
    · las 12 casillas que la 5·4 promocionó de radio no son un flag mal puesto: son **el
      interruptor de cada banda**, y pasan a ser `FormSection.enablerField`. Con el activador
      apagado, la sección se pliega — y **un alta usa 37 de los 488 widgets**, así que sin eso la
      pantalla es una lista de 481 campos;
    · etiqueta geométrica (texto a la izquierda, acotado por el widget anterior de la fila) antes
      que la IA: **74% de los campos sueltos** resueltos con cero llamadas, medido;
    · las celdas de tabla no se etiquetan una a una, heredan la cabecera de su columna;
    · arregla de camino un **fallo de orden que dejó la 5·4**: `flushLooseBefore` sólo vuelca los
      sueltos de páginas anteriores, así que los de la página en curso salen siempre detrás de sus
      tablas — DATOS DEL CLIENTE, que está arriba del todo, aparece en tercera posición.

  **El mapeo se conserva**: `MappingEditor` y `TemplateMapper.suggest()` no se retiran.

- **Tanda 5·5 — tablas en el Relleno**: filas dinámicas, catálogo local (lleva comisiones: no sale
  del dispositivo) y `cuota total = cantidad × cuota unitaria`. No bloquea el alta, porque el alta
  usa páginas 1 y 3 y ahí no hay tablas.
- **Fase 6 — firma**: manejar los campos `/Sig` del AcroForm (4 en el contrato, 2 en
  portabilidad), no sólo estampar imagen en coordenadas.

---

## 5. Pendiente de verificar (no lo des por bueno)

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
- **La dirección del DNI no es el domicilio del cliente.** El reverso del DNI trae el domicilio
  particular del representante; el de la empresa está en el 036 y en la tarjeta del NIF. En el
  caso probado las dos estaban en el mismo municipio y la misma provincia, así que **la validación
  CP↔provincia da verde con la equivocada**. Sólo la política de procedencia lo puede parar, y hoy
  `AutoFillPolicy.RISKY_SOURCES` no contempla el documento de identidad.
- **El firmante del 036 era la gestoría**, no el representante: el pie de la página 1 traía una
  razón social de un organismo y «En calidad de: Funcionario Público Habilitado» (alta tramitada
  por un PAE). El representante real estaba en la casilla 305 de la página 3. Es exactamente el
  caso que la 0.10.9 dice haber reactivado y que sigue sin comprobarse en dispositivo.
- **La inversión de nombre se rompe con dos comas.** El 036 escribe el representante como
  `APELLIDO1, APELLIDO2, NOMBRE`. La regla de la 0.10.8 parte por la **primera** coma y produce
  `APELLIDO2, NOMBRE APELLIDO1`, que es un nombre que no existe. Hay que partir por la **última**.
  Y hay confirmación cruzada gratis: la página 11 del mismo 036 lo escribe sin comas y el DNI
  separa apellidos de nombre en campos distintos.
- **Dos formas de la misma razón social** (la tarjeta del NIF añade `(EN CONSTITUCIÓN)`). No es un
  conflicto que preguntarle al usuario: es normalización.
- **Un NIF provisional valida igual.** La tarjeta lo dice expresamente y el dígito de control es
  correcto, así que `SpanishValidators` no dirá nada. Merece un aviso, no un bloqueo: Aire puede
  rechazar el alta.
- **Un teléfono y un correo para tres campos.** El 036 los declara como datos de contacto para
  avisos de la AEAT **de la entidad**; el contrato tiene `Tfno.` del cliente, `Móvil
  Representante` y `E-mail Representante` por separado. Autorrellenar los tres con lo mismo es
  plausible pero no está escrito: deben salir «por decidir».
- **Tres candidatos idénticos no son un conflicto.** El 036 repite el bloque de local una vez por
  epígrafe IAE (tres, en el caso probado) con la misma dirección. Hay que deduplicar por valor
  antes de contar alternativas, o el usuario resuelve a mano «3 opciones» que son la misma cadena.
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
