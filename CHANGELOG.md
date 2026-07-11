# Changelog — Rellenador de Contratos (Android)

Todas las versiones que han llegado a **build verde** en el workflow. Se sigue [Keep a
Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado semántico. El nombre del
artifact / APK del workflow coincide con `versionName` para poder distinguirlos.

---

## [ai-proxy 2026-07-11-b] — mismo día, sin bump de versionName de app

### Corregido (ai-proxy.php — solo servidor, no requiere nueva build de la app)
- **Groq/Qwen 413 "Request too large"**: `qwen/qwen3.6-27b` tiene un TPM (tokens/minuto,
  entrada+salida) muy ajustado de 8000 — con el techo global de 8192 tokens de salida
  recién subido, una sola imagen ya empujaba el total por encima (visto: 8225/8000).
  Fix: `callGroqSrv` ahora limita la salida a 1500 tokens SOLO para la llamada de
  visión de Groq, dejando margen de sobra para la imagen de entrada.
- **Gemini "respuesta no parseable" con JSON cortado a mitad de un valor** (visto en
  producción: `"Provincia": "VALENC` sin cerrar): el suelo de `maxOutputTokens` para
  modelos 3.x estaba en 4096, pero como el cliente ya manda 4096 por defecto,
  `max(4096, 4096)` no subía nada — el "thinking" interno se comía el presupuesto antes
  de llegar al texto visible. Subido el suelo a 8192, independiente de lo que pida el
  cliente.
- **Mistral "no se ha proporcionado ningún documento" pese a que SÍ había imagen**:
  confirmado que es alucinación del modelo (formato de imagen ya verificado correcto),
  no un fallo de nombre de modelo. Añadido detector heurístico + reintento automático
  (hasta 2 intentos) cuando la respuesta con éxito (200 OK) parece indicar "no hay
  documento" pese a haberse mandado una imagen — el muestreo no es determinista, un
  segundo intento suele bastar.

---

## [0.4.1-fallback-modelos-banner] — 2026-07-11

### Añadido (ai-proxy.php — entregado aparte, vive en cPanel)
- **Fallback de modelo por proveedor**: `callOpenAICompatSrv` ahora prueba modelos
  ALTERNATIVOS (`ALT_MODELS`) en orden si el modelo principal falla con 400/403/404
  (nombre no reconocido, bloqueado a nivel de proyecto, deprecado) — antes de rendirse
  con ese motor. Poblado para `eurouter` (varias grafías plausibles de nombre, dado
  que su catálogo no coincide 1:1 con el de Mistral directo), `grok` (grok-4.1-fast
  como reserva del flagship 4.3) y `mistral` (mistral-medium-latest de reserva). NO se
  reintenta en 429/503 (eso no es problema del NOMBRE del modelo, es cuota/demanda —
  reintentar con otro modelo no ayuda ahí).
- Diagnóstico real de esta sesión (capturas de Pablo) — la mayoría NO eran bugs:
  - Groq 403 "qwen/qwen3.6-27b blocked at project level" → hay que habilitarlo en
    console.groq.com/settings/project/limits (no hay otro modelo de visión disponible
    en Groq ahora mismo — llama-4-scout está deprecado, qwen3.6-27b es la única opción).
  - Claude 400 "credit balance too low" → cuenta de Anthropic sin saldo, no es bug.
  - Gemini 503 "high demand" → sobrecarga temporal de Google, no es bug.
  - Mistral "no se ha proporcionado ningún documento" → el formato de imagen enviado
    es correcto (verificado); es el propio modelo alucinando que no hay imagen. No es
    un problema de nombre de modelo — mistral-small-latest sigue siendo la elección
    correcta (alias que Mistral actualiza automáticamente, confirmado en su doc oficial).

### Cambiado (app Android)
- El banner rojo genérico de error YA NO se muestra para fallos de extracción por
  motor — esa información solo aparece ahora dentro de "Ver motores no disponibles"
  (colapsable, oculto por defecto), evitando duplicar el mismo mensaje dos veces.
  Otros errores no relacionados con la extracción (p.ej. exportar el PDF) siguen
  mostrando el banner normalmente, ya que no tienen panel alternativo.

---

## [0.4.0-proxy-robusto-modelos] — 2026-07-11

### Añadido (app Android)
- Resolución máxima de imagen enviada al proxy subida de 1600px a 2000px (en
  DocumentLoader y en la extracción de firma), para aprovechar el nuevo techo del
  servidor y mejorar el detalle/OCR sin cambiar el contrato de la API.

### Nota — auditoría completa del ai-proxy.php (entregado aparte, vive en cPanel)
Revisado el archivo completo. Cambios aplicados: límites subidos de forma coherente
(set_time_limit 240s, CURLOPT_TIMEOUT 180s, MAX_BODY 20MB, MAX_DOCS 12, MAX_IMG_SIDE
2000px, JPEG_QUALITY 85, RATE_MAX 60/10min, techo max_tokens 8192) y modelos
actualizados con evidencia real: Claude → claude-sonnet-5, Groq texto → openai/gpt-oss-120b
y Groq visión → qwen/qwen3.6-27b (llama-3.3-70b y llama-4-scout DEPRECADOS por Groq el
17-jun-2026 — explica varios fallos históricos), Grok → grok-4.3 (Grok 2 era ya muy
antiguo). Gemini y Mistral ya estaban correctos, sin cambios.

Hallazgo importante: el proxy permite sobrescribir modelo/endpoint de los proveedores
OpenAI-compatibles (grok/mistral/scaleway/ovh/nebius/eurouter) desde ai-proxy.config.php
(`$CFG['models'][id]`, `$CFG['endpoints'][id]`) — SI existieran overrides antiguos ahí,
anularían en silencio estos arreglos para esos 6 motores. Claude/Gemini/Groq NO tienen
este riesgo (modelo fijo en el propio ai-proxy.php). ai-proxy.config.sample.php
actualizado para incluir claves de scaleway/ovh/nebius/eurouter (antes solo mostraba
claude/gemini/groq/grok/mistral) y documentar el mecanismo de override opcional.

---

## [0.3.9-recorte-manual-firma] — 2026-07-11

### Añadido
- **Recorte MANUAL de la firma al subir una foto**: tras elegir "Elegir foto" en
  "Extraer de foto", se abre un recorte a pantalla completa donde el usuario arrastra
  el dedo para marcar exactamente la zona de la firma, antes de procesar nada. Sustituye
  la dependencia total en la localización automática por IA, que en dos rondas de ajuste
  (0.3.4 umbral de caja, 0.3.5 relajación Otsu×1.15) seguía perdiendo trazos o recortando
  mal en algunas fotos. El recorte manual NO pasa por la IA de localización — va directo
  al pipeline de tinta (aplanar + Otsu + recorte a bounding-box) sobre la región elegida.
  Botón "Foto completa" para quien prefiera el camino automático de siempre.
- **"Recortar de nuevo"**: si el resultado no convence, un botón junto a la firma
  cargada reabre el recorte sobre la MISMA foto ya elegida, sin tener que volver a
  subirla. La foto original se recuerda mientras dura la sesión.
- El recorte manual se integra con el resto de funciones ya existentes: cambiar color
  de tinta o fondo después reprocesa en vivo igual que con el flujo automático.

### Nota de build
Comprobado contra el HEAD real del repo (vía conector de lectura de GitHub) que el
fragmento de diagnóstico de Groq/Mistral (0.3.7) y el fix de Gemini (0.3.8) SÍ están
presentes en el código actual, pese a no aparecer como commits individuales en el
historial (se aplicaron como parte de entregas acumulativas). Si en una prueba no se ve
el fragmento de texto real en "respuesta no parseable", puede que el APK instalado en
el móvil sea de un build anterior — reinstalar la última versión del workflow.

---

## [0.3.8-fix-gemini-mode-real] — 2026-07-11

### Corregido — CAUSA RAÍZ REAL del 500 de Gemini encontrada (no era timeout)
Confirmado con el log de errores de PHP real del servidor (no una hipótesis más):

```
PHP Warning: Undefined array key "gemini_mode" en ai-proxy.php línea 259
PHP Fatal error: Uncaught TypeError: callGeminiSrv(): Argument #6 ($mode) must be
of type string, null given
```

El proxy NUNCA recibía el campo `gemini_mode` en la petición del cliente Android, pese
a que `ProxyRequest.geminiMode` tiene default `"g35"` y el código siempre lo manda con
ese valor. Causa: el `Json` de Retrofit (`AppModule.kt`) no tenía `encodeDefaults = true`
— y el default de kotlinx.serialization es `false` — así que CUALQUIER campo que valga
exactamente su valor por defecto (como `geminiMode="g35"` casi siempre) se OMITE del
JSON serializado por completo. El proxy PHP veía la clave inexistente, y un bug propio
en la línea 259 (reutilizaba `$in['gemini_mode']` directamente en la rama "true" del
ternario, sin el `?? 'g35'` de respaldo) convertía eso en `null`, que revienta contra
la firma `string $mode` no-nulo de `callGeminiSrv()` (el archivo usa `strict_types=1`).

Esto explica TODO lo observado: por qué solo Gemini fallaba (único motor con este
parámetro no-nulo obligatorio), por qué era consistente y no intermitente (el bug de
serialización no depende de tiempos ni cargas), y por qué el timeout de PHP (0.3.5/
0.3.6, `max_execution_time`) no lo arreglaba — nunca fue un problema de tiempo.

### Fix (dos lados, complementarios)
- **Android** (`AppModule.kt`): `encodeDefaults = true` en el `Json` — ahora el JSON
  enviado refleja de verdad los valores que el código dice que manda.
- **PHP** (aplicar manualmente en `ai-proxy.php`, línea 259 — vive en el hosting de
  Pablo, fuera de este repo): usar una variable intermedia para el valor con `??` de
  respaldo, en vez de re-consultar `$in['gemini_mode']` sin respaldo en el ternario.
  Esto protege al proxy de cualquier cliente (presente o futuro) que omita el campo.

---

## [0.3.7-diag-mistral-snippet] — 2026-07-11

### Corregido
- **`maxTokens=4096` (0.3.6) NO resolvió "Mistral: respuesta incompleta"** — confirmado
  en una prueba real posterior con el mismo fallo. La hipótesis del corte por límite de
  tokens queda descartada; la causa real de que `AiJsonParser.parse()` devuelva null es
  otra, y sin ver el texto crudo no se puede saber cuál.
- **Diagnóstico ampliado**: cuando el parseo del JSON falla, el mensaje agrupado ahora
  incluye un fragmento del texto real que devolvió el motor (`"respuesta no parseable —
  \"<primeros 180 caracteres>\""`), mismo principio que `realErrorMessage()` para errores
  HTTP (0.3.1). Sin esto no se puede distinguir entre: JSON cortado a medias, texto plano
  sin JSON, un mensaje de error del propio motor camuflado como éxito, o un formato
  inesperado — cada causa necesitaría un fix distinto.

### Investigación Gemini — el aumento de max_execution_time a 240s NO resolvió el 500
Confirmado en prueba real posterior al cambio en cPanel: Gemini sigue devolviendo HTTP 500
sin cuerpo. Esto apunta a que el límite real no es (solo) el `max_execution_time` de PHP
— sospecha siguiente: **timeout de proxy/FastCGI de Apache o LiteSpeed**, que en muchos
hostings cPanel es un ajuste SEPARADO del PHP y con su propio tope, no necesariamente
visible ni modificable desde el MultiPHP INI Editor. Pendiente: revisar el log de errores
de cPanel para la prueba más reciente (post-240s) y, si no aparece ya el mensaje de tiempo
excedido, consultar con soporte del hosting sobre el timeout de proxy/FastCGI del dominio.

---

## [0.3.6-maxtokens-mistral] — 2026-07-11

### Corregido
- **Mistral: "respuesta incompleta"** en la extracción — a diferencia de los otros
  fallos (400/404/429/500), Mistral respondía HTTP 200 (éxito) pero el JSON no se podía
  parsear. Causa probable: `maxTokens=2048` cortaba a mitad la respuesta para un JSON
  completo (sugerencias + alternativas + paquetes puede ser verboso en contratos con
  varios documentos). Subido a `maxTokens=4096`, el propio techo que ya aplica el proxy
  (`min(4096, ...)`) — antes se usaba solo la mitad del margen permitido.
- `locate_signature` (SignatureLocator) se deja en 300 tokens: no tiene este riesgo,
  la respuesta esperada es solo un JSON pequeño de coordenadas.

### Pendiente (diagnóstico en curso, no app-side)
- **EUrouter 404** — "Model 'mistral-small-latest' not found or has no available
  providers". Configuración del proxy PHP (`ai-proxy.config.php`), no de la app.
- **Groq 429** — cuota de tokens/minuto casi agotada (28863/30000 usados). Externo,
  no requiere cambio de código; se resuelve con tiempo o subiendo de plan en Groq.
- **Gemini 500 sin mensaje real** — el diagnóstico de 0.3.1 (`realErrorMessage()`) no
  encuentra body JSON legible para este fallo concreto, lo que apunta a un error de PHP
  a nivel más bajo (excepción no capturada / timeout de cURL) antes de que el proxy
  llegue a formatear la respuesta de error. Requiere revisar el log de errores del
  servidor (cPanel) — no se puede diagnosticar más desde el cliente.

---

## [0.3.5-restaura-umbral-otsu] — 2026-07-11

### Corregido — auditoría completa del historial (otro fix perdido, distinto de 0.3.2)
Revisando TODO el historial de commits con el conector de GitHub (list_commits/get_commit),
se encontró un grupo de 8 commits de otra sesión en la noche del 07-09 (21:53-23:44) que
nunca se habían incorporado — distintos de la regresión ya corregida en 0.3.2. La mayoría
eran arreglos mecánicos de sus propios errores de compilación (llaves huérfanas, residuos
de `toSignatureData`) o un experimento de upscale a 1500px que ELLOS MISMOS revirtieron
(por eso no hace falta restaurarlo). Pero uno seguía siendo relevante y perdido:

- **`processInk` umbral demasiado estricto**: la condición para descartar un píxel como
  "fondo" era `lum > threshold`. Un commit de esa noche la relajó a `lum > threshold * 1.15`
  para capturar el trazo completo — sin este margen, bordes antialiaseados o trazos algo
  más claros de la tinta real se descartaban, dejando solo el núcleo más oscuro. Esto
  encaja con el fragmento irreconocible reportado (0.3.4 solo arregló la localización y
  el recorte; este es un tercer factor independiente sobre la MISMA foto de prueba).
- Verificado con el conector de GitHub (no solo el CHANGELOG) leyendo el diff exacto del
  commit `e2cbc7f` — cambio de una sola línea, restaurado sin reconstrucción de memoria.

### Nota sobre el conector de GitHub
Confirmado en esta sesión: el conector tiene permiso de LECTURA (`list_commits`,
`get_commit`, `get_file_contents` — funcionan perfectamente, sin caché desactualizada) pero
NO de escritura (`create_or_update_file` da 403 "Resource not accessible by integration").
Se sigue usando ZIP + terminal de Pablo para aplicar cambios; el conector de lectura se usa
para verificar el HEAD real y auditar el historial antes de generar cada entrega.

---

## [0.3.4-fix-localizacion-firma] — 2026-07-11

### Corregido
- **Firma extraída de foto irreconocible (fragmento diminuto sin relación con el trazo
  real)**: comparando la firma real (foto aportada) contra el resultado en la app, el
  recorte capturaba solo una esquina minúscula. Causa: `SignatureBox.valid` aceptaba
  cualquier caja con w>2%,h>2% — demasiado permisivo; un error de localización de la IA
  (fragmento equivocado, pequeño) pasaba como "válido" y el recorte a bounding-box
  (0.3.3) lo acotaba aún más sobre esa región ya errónea.
- **Fix 1**: `SignatureBox.valid` ahora exige w>15%,h>8% — descarta cajas demasiado
  pequeñas para ser una firma real localizada con fiabilidad.
- **Fix 2**: cuando no hay caja fiable (`box == null`), ya NO se ofrece la foto cruda
  sin procesar. Se aplica el mismo pipeline completo (aplanado + Otsu + recorte a
  bounding-box) a la foto ENTERA. Esto resuelve muy bien el caso de una foto que YA es
  solo la firma aislada sobre fondo claro (sin documento alrededor) — no hace falta que
  la IA "localice" nada dentro de una imagen que ya es solo la firma.

---

## [0.3.3-fix-crop-firma] — 2026-07-11

### Corregido
- **Firma de foto se ampliaba y recortaba en exceso al aplicarla al PDF final**: la
  0.3.2 desactivó el recorte a bounding-box (`applyBoundingCrop = false`) cuando el
  locator ya daba una caja, pensando que evitaba un problema de 0.2.0 (desviarse a una
  esquina por sombras). En la práctica, si la caja del locator viene floja (con margen
  vacío alrededor del trazo real), el resultado es un lienzo grande casi en blanco con
  el trazo diminuto en el centro — que luego el encaje "letterbox" (0.2.2) amplía para
  llenar la caja calibrada del contrato, recortando/deformando el trazo real.
- **Fix**: `applyBoundingCrop` vuelve a estar activo SIEMPRE tras el recorte del locator
  (`extractSignatureFromPhoto` y `reprocessSignatureFromRaw`). El riesgo de "esquina
  desviada" de 0.2.0 solo aplicaba a fotos COMPLETAS sin recortar — ese caso ya no se
  procesa en absoluto (fallback de 0.2.0: se ofrece la foto tal cual sin tintar). Sobre
  una región ya acotada por el locator, recortar de nuevo al trazo real es seguro y
  necesario para que la firma final no quede minúscula dentro de un recuadro vacío.

---

## [0.3.2-restaura-0.2.x] — 2026-07-11

### Corregido — restaura la regresión documentada en 0.3.1
La fusión "Ajustes + letterbox real" (commit "Fusion...") había mezclado la pantalla de
Ajustes con una versión de `MultiAiExtractor.kt`/`SignatureLocator.kt`/`ReviewStep.kt`/
`SignaturePageDetector.kt` anterior a 0.2.0, revirtiendo sin querer varias mejoras. Todas
reconstruidas ahora usando el CHANGELOG (0.2.0/0.2.1) como especificación exacta:

- **MultiAiExtractor**: short-circuit por motor (`dead` set — un motor que falla 4xx/5xx
  no se reintenta en la sesión), backoff cooperativo de 2,5 s en 429, orden fiable
  (Groq → Mistral → Claude → Gemini → Scaleway → EUrouter), errores agrupados por motor
  (una línea con el último estado, no una por documento). Aprovecha `HttpException.
  realErrorMessage()` (0.3.1) para que el detalle incluya el mensaje real del proveedor.
- **SignaturePageDetector**: la página 24 (índice 23) se fuerza como candidata siempre
  que el documento tenga ≥24 páginas — no tiene ningún campo AcroForm propio, así que la
  detección estructural nunca podía encontrarla por ese camino.
- **SignatureLocator**: reordenado a Mistral → Scaleway → Claude → Gemini → Grok (Groq
  excluido: no tiene visión real, es un motor de texto que "especularía" el JSON).
- **Actualización en vivo de tinta/fondo**: se guarda el bitmap "crudo" (antes de tintar)
  tanto para fotos como para dibujos a mano; cambiar color o fondo reprocesa de inmediato
  sin volver a llamar a la IA de localización (`reprocessSignatureFromRaw()`).
- **Fallback razonable cuando el locator falla**: si ningún motor localiza la firma en una
  foto, ya NO se aplica `processInk` a la foto entera (sacaba resultados basura); se
  ofrece la foto original tal cual sin tintar.
- **`applyBoundingCrop` opcional** en `processInk`/`fromPhoto`: desactivado cuando la
  imagen ya viene recortada de forma fiable por `SignatureLocator`, evitando el doble
  recorte que desviaba el resultado a una esquina.
- **Panel de detalle de motor caído** restaurado en `ReviewStep` (colapsable, bajo un
  botón "Ver motores no disponibles (N)"), con el mensaje real de cada motor.
- **Chip informativo**: "Firma cargada ✓ · lista para N páginas" sustituye al chip mudo.
- **Previsualización de firma reubicada**: justo tras elegir Dibujar/Extraer, antes de
  las opciones de color/fondo.

---

## [0.3.1-diag-errorbody] — 2026-07-10

### Añadido
- **Instrumentación de diagnóstico para 400/404/500 de cualquier motor** (investigación Gemini): `ai-proxy.php` ya reenvía el mensaje real del proveedor upstream (`{"ok":false,"error":"Gemini: <mensaje real de Google>"}`) con el código HTTP real de Google/Anthropic/etc. — pero Retrofit, al ver un código no-2xx, lanza `HttpException` sin deserializar ese body, y nadie lo leía (`e.message` por defecto solo da algo genérico tipo "HTTP 500 Internal Server Error"). Nuevo `HttpException.realErrorMessage()` en `MultiAiExtractor.kt` lee `errorBody()` a mano y extrae el campo `error`. Se usa en:
  - `MultiAiExtractor.kt`: el banner de error de `WizardScreen` ahora incluye el mensaje real además del código HTTP.
  - `SignatureLocator.kt`: antes se tragaba cualquier fallo en silencio (`runCatching{}.getOrNull() ?: continue`, sin log); ahora loguea motor + código + mensaje real con `Log.w`.
- Verificado contra la documentación oficial de Gemini (jul 2026) que el payload que arma `callGeminiSrv()` en el proxy es correcto: modelo `gemini-3.5-flash` es GA vigente, `thinkingConfig.thinkingLevel` (sin `thinkingBudget` a la vez, evitando el 400 documentado) y formato `inline_data`/`mime_type` coinciden con los ejemplos oficiales de `generativelanguage.googleapis.com`. No se encontró ningún parámetro mal formado por inspección estática — el paso obligado ahora es leer el mensaje real que esta build ya expone.

### ⚠️ Regresión detectada en 0.3.0 (sin corregir en esta build — pendiente de decisión)
El commit "Fusion: Ajustes + letterbox real" mezcló la nueva pantalla de Ajustes con una
versión **anterior a 0.2.0** de `MultiAiExtractor.kt`/`SignatureLocator.kt`/`ReviewStep.kt`,
revirtiendo sin querer:
- El short-circuit por motor caído y el backoff cooperativo en 429 (`dead`/`perProviderStatus` de 0.2.0) — un motor roto vuelve a fallar una vez por cada documento en vez de una sola vez.
- El panel de detalle "Motores no disponibles" en Revisión IA (`ReviewStep.kt`) desapareció por completo — solo queda el banner genérico de `state.error`.
- El orden del `SignatureLocator` volvió a `[Claude, Gemini, Groq, Grok, Mistral]` (Claude/Gemini primero pese a estar caídos hoy; Groq de vuelta en la lista pese a no tener visión real — ver nota en 0.2.1).
- `CHANGELOG.md` no se actualizó para 0.3.0 (versionName saltó de 0.2.2 a "0.3.0-ajustes-letterbox" sin entrada aquí).
No se corrige aquí para no mezclar con el objetivo único de esta build (instrumentación Gemini). Restaurar si se confirma que se quiere.

### Pendiente (siguiente paso de la investigación Gemini)
- Con esta build, reproducir el fallo de Gemini y capturar el mensaje real en el banner de error (o en logcat, tag `MultiAiExtractor`/`SignatureLocator`) para aislar la causa exacta (cuota, billing, argumento inválido, etc.) y proponer el fix quirúrgico correspondiente — en el PHP o en el cliente, según lo que diga Google.

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
