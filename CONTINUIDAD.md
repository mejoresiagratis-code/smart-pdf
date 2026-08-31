# Continuidad — Rellenador PDF (Android)

> **Para qué es esto.** Arranque de sesión nueva con Claude. Pégale el enlace de este fichero
> o su contenido; con eso y el repo tiene el contexto completo. **No hace falta reenviar los
> PDFs de Aire**: su análisis está en `docs/ANALISIS_FORMULARIOS_AIRE.md`.
>
> **Actualizado**: 2026-08-31, tras `0.10.5-etiquetado-enganchado` (⚠️ pendiente de verde). Este
> documento **caduca**: la primera regla de abajo existe precisamente porque lo que aquí se
> afirma puede estar viejo.

Habla en **español de España**.

---

## 1. Lo primero, siempre

Clona el repo y **verifica el estado real por `git log`. No te fíes de ningún documento,
incluido este.**

```
git clone https://github.com/mejoresiagratis-code/smart-pdf
cd smart-pdf && git log --oneline -8
```

El último commit **de código** debería ser `0.10.5-etiquetado-enganchado` · versionCode 75. Por
encima puede haber commits solo de documentación, que no suben versión. Si el último código no es
ése, este documento está desfasado: manda `git log` y la cabecera del `CHANGELOG.md`.

### ⚠️ Lo segundo: comprobar el build de la 0.10.5

**La 0.10.5 está pendiente de verde en Actions.** Míralo antes de escribir código nuevo. La
0.10.4 anterior sí está verificada.

Lo que sí se comprobó en local con `kotlinc` (ver la técnica en la sección 6): `data/model`
completo, `FieldLabeler` y `VisionLabelPass` typecheckean sin errores ni avisos, y una prueba de
comportamiento contra el `SchemaLabeling` real confirma la traducción de identificadores. Lo que
**no** se pudo verificar es todo lo que depende de Android o de pdfbox de verdad: el render de
páginas, `pageSize()` y la llamada al proxy. Si el build falla, mira ahí primero — y en
`LabelEditorScreen`, que es lo único con Compose de la tanda.

Si sale verde, lo siguiente no es código: es **probarlo en el móvil con un PDF real de Aire**.
La lógica está verificada, pero la calidad de las etiquetas que devuelve la visión sólo se ve
usándolo.

---

## 2. Qué leer, en este orden

1. `ROADMAP.md` — plan multi-formulario, decisión de arquitectura de expediente, fases.
2. `docs/ANALISIS_FORMULARIOS_AIRE.md` — análisis de los PDFs de Aire, verificado contra los
   ficheros reales. Es el brief técnico de las fases que quedan.
3. `CHANGELOG.md`, entradas **0.9.4 a 0.10.4**.
4. `docs/ESTADO_Y_GUIA_DE_CONTINUIDAD.md` — arquitectura e historia. **Ojo**: su bloque
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
| 0.10.5 | Fase 3 **cerrada**: `VisionLabelPass` engancha el etiquetado por visión ⚠️ sin verde |

### Qué está enganchado y qué no

**Enganchado desde la 0.10.4**: Ajustes › Herramientas (beta) › «Analizar y etiquetar un PDF» →
SAF → `PdfFieldInspector.inspect()`/`pageCount()` → `TemplateFingerprint` → esquema guardado en
`schemas_v1` si ese PDF ya pasó, o `FormSchemaBuilder.build()` → `LabelEditor` → persistir. Con
su propio `LabelEditorViewModel` y su propia ruta `etiquetas` en el NavHost.

Desde la 0.10.5 ese mismo editor tiene un botón **«Etiquetar con IA»** que pasa `FieldLabeler`
sobre el esquema entero (`VisionLabelPass`), así que la fase 3 ya está enganchada.

**NO enganchado**: `FillStep` sigue recorriendo las 6 secciones fijas de `CANON`. Subir un PDF de
Aire detecta sus campos y ya se pueden etiquetar, pero el paso de Relleno sigue mostrando los del
contrato de Orange. `WizardViewModel`, `WizardState` y los cinco pasos siguen **sin tocar** desde
la 0.9.8.

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
- **Fase 5 — relleno dinámico**: conectar `FillStep` al `FormSchema` en vez de a `CANON`. Tablas
  con filas dinámicas, selector de catálogo, `cuota total = cantidad × cuota unitaria`. Es la que
  hace que un PDF de Aire se rellene de punta a punta, **y la que toca `WizardViewModel` (1126
  líneas)**. Con diferencia la más arriesgada de las que quedan: tanda propia, nada más dentro.
- **Fase 6 — firma**: manejar los campos `/Sig` del AcroForm (4 en el contrato, 2 en
  portabilidad), no sólo estampar imagen en coordenadas.

---

## 5. Pendiente de verificar (no lo des por bueno)

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
- **Basura en la raíz**: `hotfix-contractstep-scope.zip` y `v0.7.7-estructura-detectada.zip`,
  1,1 MB commiteados desde la 0.7.7. Restos de mover ficheros a mano, no un método. Borrarlos y
  añadir `*.zip` al `.gitignore` es una tanda de dos minutos.

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
