# Changelog — Rellenador de Contratos (Android)

Todas las versiones que han llegado a **build verde** en el workflow. Se sigue [Keep a
Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado semántico. El nombre del
artifact / APK del workflow coincide con `versionName` para poder distinguirlos.

---

## [0.2.2-stamp-letterbox] — 2026-07-10

### Corregido
- **La firma se recortaba o ampliaba en exceso al generar el PDF final**, aunque la previsualización se viera bien. Causa raíz: `AcroFormFiller` calculaba el alto del estampado como `w * signature.aspectRatio` — es decir, forzaba la altura a partir del aspect ratio real de la imagen de la firma procesada (que varía mucho según el trazo, el recorte, la foto de origen), en vez de respetar el tamaño real del hueco de firma del contrato. Una firma con trazo muy ancho y fino, o muy vertical, deformaba o desbordaba el hueco calibrado.
- **Fix**: `SignatureStamp` ahora tiene también `heightRel` (antes solo `widthRel`), definiendo una CAJA fija calibrada contra el contrato real (0.256 × 0.114 en las 5 páginas de firma). La firma se escala en modo *letterbox* — cabe dentro de esa caja respetando su propio aspect ratio, sin deformarse, tomando como límite el lado (ancho o alto) que primero se alcance. Esto es coherente con lo que ya se ve en la previsualización (que muestra la firma como el bitmap la tiene, sin forzarla), y ahora el PDF final refleja fielmente el mismo resultado.
- Slider manual de "Tamaño" en `SignatureStep` ahora escala la caja completa (ancho + alto proporcional) en vez de solo el ancho, para no reintroducir deformación al ajustar a mano.

---

## [0.2.1-firma-fix] — 2026-07-10

### Corregido
- **Página 24 no aparecía en "páginas de firma detectadas"**: verificado con pypdf contra `contrato-relleno-a1.pdf` que la página 24 **no tiene ningún campo AcroForm propio** — la detección estructural (basada en campos multipágina) nunca podía encontrarla por ese camino. Se fuerza ahora como candidata siempre que el documento tenga ≥24 páginas, independientemente de la detección estructural.
- **Coordenadas de firma corregidas a partir de una nueva calibración más precisa**: la calibración anterior (0.2.0) usaba la esquina superior-izquierda de la imagen de firma como si fuera el centro (`xRel`/`yRel` esperan CENTRO, ver `AcroFormFiller`). Con pdfplumber se identificó el rótulo "EL DISTRIBUIDOR" real en cada página (aislando bien "EL"+"DISTRIBUIDOR" de otros textos en la misma línea, ya que hay páginas con dos rótulos distintos a la misma altura — p.ej. "XFERA MÓVILES" y "EL DISTRIBUIDOR" comparten renglón en las páginas 30/33), se cruzó con la imagen de firma inmediatamente asociada, y se convirtió a centro real:
  - Página 24: xRel 0.275, yRel 0.463 (izquierda)
  - Página 30: xRel 0.722, yRel 0.261 (**derecha** — el bloque del distribuidor está a la derecha en esta página)
  - Página 33: xRel 0.220, yRel 0.940 (izquierda, muy abajo)
  - Página 45: xRel 0.222, yRel 0.853 (izquierda)
  - Página 54: xRel 0.183, yRel 0.886 (izquierda)
  - La firma ahora queda centrada e inmediatamente debajo del rótulo, no desplazada a un lado.
- **Firma sin actualización en vivo al cambiar color/fondo**: `setInkColor` y `setSigBackground` solo actualizaban el estado sin reprocesar el bitmap. Ahora se guarda el bitmap "crudo" (antes de tintar) tanto para fotos como para dibujos a mano, y cambiar color o fondo reprocesa inmediatamente sin volver a llamar a la IA de localización.
- **Locator de firma reordenado**: Claude/Gemini están caídos (400/500) y Groq no tiene visión real (es un motor de texto que "especula" el JSON). El orden pasa a ser Mistral → Scaleway → Claude → Gemini → Grok, priorizando los que sí tienen visión y funcionan hoy.

### Añadido
- **Confirmación visual de firma cargada**: chip "Firma cargada ✓ · lista para N páginas" junto a la previsualización, sustituye al chip mudo "Firma preparada ✓" que no informaba nada útil.
- **Previsualización de firma reubicada**: ahora aparece justo debajo del selector Dibujar/Extraer de foto, antes de las opciones de color/fondo — el usuario ve el resultado inmediatamente en vez de tener que bajar mucho en la pantalla.

### Pendiente (fuera de esta tanda, para no sobrecargar — ver plan de fases)
- Pinch-to-zoom y arrastre táctil directo sobre la miniatura de previsualización de firma (hoy solo existe arrastre sobre el marcador ✍ en la previsualización completa del PDF de 54 páginas).
- Separar "colocar página por página, una a una, en cualquier momento" de "rellenar solo las que faltan" como dos acciones distintas del botón de estampado masivo (hoy `stampAllPages` sobreescribe todas).

---

## [0.2.0-firma] — 2026-07-10

### Corregido
- **Firma recortada a un puntito**: la firma extraída de foto se veía como un cuadro casi vacío con un píxel en el centro. Causa: `processInk` recortaba a bounding box mínimo aun cuando `SignatureLocator` ya había recortado con la caja de la IA, y sobre fotos completas cualquier píxel oscuro (sombra, arruga) desviaba el bounding box a una esquina. Ahora `fromPhoto` acepta `applyBoundingCrop` opcional, y `extractSignatureFromPhoto` lo desactiva si ya hay caja del locator.
- **Fallback razonable cuando el locator falla**: si Claude/Gemini caen y ningún motor localiza la firma, ya no se aplica `processInk` a toda la foto (que sacaba resultados basura), sino que se ofrece la foto original tal cual y se avisa al usuario.
- **Errores agrupados por motor** en `MultiAiExtractor`: en vez de listar cada 400/500/429 repetido por documento, se muestra una sola línea con el último estado por motor. Además:
  - Short-circuit: un motor que falla con 4xx/5xx no se reintenta en la sesión de extracción.
  - Backoff cooperativo de 2,5 s cuando algún motor devuelve 429, para no quemar la cuota `RATE_MAX=30/10min` del proxy.
  - Orden fiable: Groq → Mistral → Claude → Gemini → Scaleway → EUrouter, prioriza los que suelen ir bien.

### Añadido
- **Coordenadas de firma calibradas** con `contrato-relleno-a1.pdf` (referencia real). Nuevos defaults por página:
  - Página 24: xRel 0.147, yRel 0.406 (izquierda-centro)
  - Página 30: xRel 0.594, yRel 0.204 (derecha — el rótulo "EL DISTRIBUIDOR" en esa página está a la derecha, no a la izquierda)
  - Página 33: xRel 0.092, yRel 0.883 (izquierda-abajo)
  - Página 45: xRel 0.093, yRel 0.796
  - Página 54: xRel 0.055, yRel 0.829
  - Ancho constante: wRel 0.256, aspect ratio h/w = 0.44 (más apaisado que antes).
- **Panel de detalle de motor caído** en el UI de Revisión IA: muestra el mensaje del proxy (task, código HTTP y snippet del body) para diagnosticar 400/500 sin necesidad de `adb logcat`.
- **Nombrado versionado del APK y del artifact** del workflow: `rellenador-<versionName>.apk` en vez de `app-debug.apk`.
- **CHANGELOG.md** (este archivo), retroactivo desde el arranque del proyecto.

### Notas técnicas
- El logging BODY de OkHttp sigue activo en debug. Con `adb logcat -s okhttp.OkHttpClient` (o el visor del móvil filtrado por tag `okhttp`) se ve el JSON exacto que se envía al proxy en cada llamada.
- El fix de errores no arregla los 400/500/404 upstream — arregla la cascada. Los 3 motores rotos siguen rotos, pero ya no ahogan al resto.

---

## [0.1.7-fix-nullable] — 2026-07-10

### Corregido
- **Build rojo por `Float?` en `PdfPreview.kt`**: `var curX = stampXRel` y `var curY = stampYRel` heredaban `Float?` de la firma nullable de `SignatureStamp`, y luego el operador `+` con `dragAmount` fallaba con `Operator call is prohibited on a nullable receiver of type 'Float?'`. Fix mínimo: `stampXRel ?: 0.5f` para forzar `Float` no-nullable.

---

## [0.1.6-post-tanda-f] — 2026-07-09

### Corregido
- **HTTP 400/500/429 al extraer desde foto**: `DocumentLoader` mandaba las fotos del cliente sin redimensionar (varios MB de móvil a resolución completa), a diferencia de las páginas de PDF rasterizadas. Añadido `downscaleIfNeeded(1600)` antes de codificar en base64, en `DocumentLoader` y en `extractSignatureFromPhoto`.
- **Preview visual de la firma procesada** en `SignatureStep` (antes solo había un chip de texto sin imagen).
- **Gesto de arrastre de la firma**: antes respondía en toda la página e interfería con el scroll de la lista de páginas. Ahora vive solo en el marcador ✍: primer toque lo selecciona (se resalta) y solo entonces se puede arrastrar, acumulando posición localmente para no cancelar el gesto a mitad de camino.

---

## [0.1.5-tanda-f] — Persistencia

### Añadido
- **Perfil comercial y templates por fingerprint** (`ContractProfile.kt` + `TemplateFingerprint`): huella = nº páginas + nombres de campo normalizados.
- **PrefsRepository**: `saveTemplate` / `findTemplate` (reaplica mapeo automático si ya se vio ese PDF), `saveToHistory` / `listHistory` / `deleteFromHistory`, `exportProfileJson` / `importProfileJson`.
- **HistoryPanel.kt**: diálogo con historial de contratos desde el botón "Historial" en `FillStep`.

### Notas
- Se limpiaron archivos huérfanos de una fase anterior mal llamada "Tanda F" (`ui/history/HistorialScreen.kt`, `ui/history/HistorialViewModel.kt`, `ui/settings/AjustesScreen.kt`, `ui/settings/AjustesViewModel.kt`). No deben volver a aparecer.

---

## [0.1.4-tanda-e] — Firma avanzada

### Añadido
- `SignatureProcessor` ampliado con Otsu automático, `flattenIllumination` (corrige iluminación desigual en fotos), `processInk` (tintado con alpha graduado + recorte a bounding box + fondo transparente/blanco), `fromPhoto` (pipeline completo).
- Color de tinta (azul, negro, azul claro) y fondo (transparente, blanco) elegibles en `SignatureStep`.
- Firmas guardadas reutilizables vía `PrefsRepository.saveSignature` / `listSignatures` / `getSignature` (DataStore).

---

## [0.1.3-tanda-d] — Extracción fina

### Añadido
- `DateAutofill.kt`: autorrelleno de fecha actual (día/mes en letras español, último dígito del año) para campos vacíos — verbatim de `autoFillDates()` de la web.

### Cambiado
- `MultiAiExtractor`: votación de tipo de identificación por mayoría (antes cogía el primero), corte inteligente (`earlyStop`) que deja de llamar motores cuando todos los campos ya están cubiertos.
- `ReviewStep`: tocar un candidato ya seleccionado lo desmarca (tap-again-to-deselect).

---

## [0.1.2-tanda-c] — Previsualización del PDF

### Añadido
- `PdfPageRenderer.kt`: renderiza páginas bajo demanda (caché LRU de 4) para no cargar las 54 de golpe.
- `PdfExporter.generatePreview()`: genera un PDF temporal para preview.
- `PdfPreview.kt`: LazyColumn de 54 páginas con badge "✍" en páginas de firma.

### Notas
- Altura fija en el contenedor (560dp) para evitar el crash clásico de Compose (`LazyColumn` dentro de scroll vertical sin restricción).

---

## [0.1.1-tanda-b] — Detección real de huecos de firma

### Añadido
- `SignaturePageDetector.kt`: usa pdfbox (widgets multipágina) + `PDFTextStripper` para localizar los huecos reales.
- `WizardViewModel`: `detectSignaturePages()`, `addSignPage` / `removeSignPage`, `stampAllPages` (masivo), `stampOnePage` (una a una).

### Corregido
- `det.signAnchors` → `det.anchors` (el campo de la `data class Detection` se llama `anchors`, no `signAnchors`).

### Notas técnicas
- Los huecos de firma reales del contrato son las páginas **24, 30, 33, 45, 54** (no solo la 24 fija). Verificado cruzando campos AcroForm multipágina con la presencia del rótulo "EL DISTRIBUIDOR" en el texto.

---

## [0.1.0-fase-4] — Mapeo de PDF propio + campos verificados

### Añadido
- `TemplateMapper.kt`: auto-mapeo de nombres reales de un PDF de usuario a claves canónicas por similitud normalizada (sin acentos, minúsculas, espacios colapsados).
- `MappingEditor.kt`: UI para revisar/corregir el mapeo.

### Corregido
- Verificado con la skill `pdf` contra el contrato real: **23 campos (20 texto + 3 checkbox)**. `Email Comercial` (1 espacio) y `Email  Facturación` (2 espacios) son **campos distintos**, no uno solo. Checkboxes `NIF` / `CIF` (valores `/On` / `/Off`) se marcan solo si `tipo_identificacion` es concluyente.

---

## [0.0.9-fase-3] — Paquetes en bloque

### Añadido
- `PackageApplier`: aplica un paquete completo (dirección fiscal / comercio / empresa / persona / banco) de un toque en Revisión.
- Bloque `_2` (Dirección_2 / CP_2 / Población_2 / Provincia_2 = comercio/PdV) añadido a `CANON`.

---

## [0.0.8-fase-2] — Firma básica + PDF final

### Añadido
- `AcroFormFiller.generate()`: rellena AcroForm + estampa firma con pdfbox-android.
- `SignatureCanvas.kt`: canvas de dibujo para firma manuscrita.
- `PdfExporter`: genera a `filesDir/output`, comparte por FileProvider o guarda vía SAF.

---

## [0.0.7-fase-1] — Wizard de 5 pasos

### Añadido
- Flujo completo: Contrato → Documentación → Revisión IA → Relleno → Firma.
- `WizardViewModel` + `WizardState` orquestan el flujo.
- `ExtractionPrompt.kt` con el prompt de extracción **verbatim** de la web (reglas de dirección fiscal vs `_2`, autónomo vs CIF, formato IBAN/CP/CNAE).
- `ContractFields.CANON` con las claves canónicas (con dobles espacios frágiles preservados).
