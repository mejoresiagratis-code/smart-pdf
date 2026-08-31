# Estado y guía de continuidad — Rellenador Android (nativo)

> **Este documento tiene dos partes.** La primera es el **estado actual** (mantener al
> día). Todo lo que hay a partir de «ARCHIVO HISTÓRICO» es el registro de las tandas
> originales de la migración — valioso como referencia, pero NO refleja el estado
> presente (habla de 15 campos canónicos, versionCode 5, y de "pendientes" que llevan
> meses hechos). Para el estado por versión, la fuente de verdad es `CHANGELOG.md`; para
> lo próximo, `ROADMAP.md`.

## ESTADO ACTUAL (2026-08-05 · v0.7.10-dns-fallback-doh · versionCode 48)

- **Los 5 pasos del wizard están completos y en uso real** (Contrato con mapeo de PDF
  propio, Documentación, Revisión IA con bloques, Relleno con validación en vivo y
  candidatos alternativos, Firma con estampado calibrado en pág. 24/30/33/45/54 y
  previsualización de 54 páginas). CANON tiene **22 campos** (no 15).
- **Producción**: proxy en `datingtrck.com/pdf/ai-proxy.php` (BanaHosting/cPanel,
  usuario `obvzudpy`), config protegida en `/home/obvzudpy/datingtrck.com/proxyconfig/`.
  El proxy NO vive en el repo; se despliega por FTP/cPanel.
- **Motores activos en servidor** (GET del proxy, ago 2026): `gemini:true`, `groq:true`;
  resto sin clave activa.
- **Red**: `Ipv4PreferredDns` — IPv4 primero (dominio sin AAAA; NAT64/5G) + fallback
  DNS-over-HTTPS por IP literal ante cachés DNS negativas (v0.7.10).
- **Tipo de documento en el diálogo de análisis**: por contenido (texto PDF, PDFBox) con
  prioridad, y por IA (visión, `tipo_documento`) para fotos/escaneos (v0.7.8/0.7.9).
- **Flujo de trabajo vigente**: Claude tiene push directo por token (PAT) cuando Pablo lo
  facilita; si no, ZIPs completos que Pablo aplica con `git pull && unzip -o && git
  commit && git push`. El juez del build es GitHub Actions (aquí no se compila Android).
  El workflow `.github/workflows/android.yml` es de Pablo — no tocarlo sin pedirle el
  contenido actual.
- **Paridad web pendiente**: replicar `tipo_documento` en el prompt de la web.

## Cómo arrancar una sesión nueva
1. `git clone` fresco del remoto real y `git log --oneline` — nunca asumir el estado.
2. Leer la cabecera de `CHANGELOG.md` (última versión) y el `ROADMAP.md` (prioridades).
3. Si la tanda toca el prompt, el proxy o la web: pedir a Pablo los ficheros reales
   (el proxy y la web no viven en este repo).
4. Versionado: cada tanda con build verde sube `versionCode`+`versionName` y añade su
   entrada al CHANGELOG. Hotfix sobre versión que nunca llegó a verde NO incrementa.
5. **Formato del mensaje de commit.** El **título** (primera línea) es exactamente
   `rellenador-<versionName>`, nada más. Con eso coinciden cuatro cosas que antes no
   coincidían: título del commit, título del run en Actions, nombre del zip del artefacto y
   nombre del APK de dentro. Ejemplo: `rellenador-0.9.8.1-arreglo-de-compilacion`.

   El **cuerpo** va tras una línea en blanco y se mantiene **corto**: qué cambia y por qué, en
   unas pocas líneas. El razonamiento largo (verificaciones, tablas, alternativas descartadas)
   va al `CHANGELOG.md`, que es donde se busca. Antes se duplicaba entero en el cuerpo del
   commit y, como el `run-name` del workflow usaba el mensaje **completo**, el título del run
   salía como un muro de texto cortado.

   El workflow ya no fija `run-name`: sin él, GitHub usa por defecto la primera línea del
   commit, que es justo lo que se busca. Las expresiones de Actions no saben partir cadenas, así
   que recortarla desde el YAML no era posible.

---

# ARCHIVO HISTÓRICO (tandas de la migración original — no refleja el estado actual)

## Hito alcanzado
La app compila, instala y arranca. Migración nativa Kotlin+Compose de la web
`rellenador-pro.html`. Fase 1 (pasos 1-3 del flujo) implementada, con andamiaje
de navegación de los 5 pasos ya cableado.

## Flujo (fiel a la app web)
1. **Contrato** — contrato por defecto (assets/contrato-base.pdf, 54 pág.) o PDF del usuario.
2. **Documentación** — el cliente aporta fotos/PDF; se eligen motores IA (los que
   tienen clave en servidor, vía GET al proxy).
3. **Revisión IA** — extracción multi-motor; toast de confirmación por campo con
   candidatos y consenso de motores; opción "dejar en blanco".
4. **Relleno** — todos los campos canónicos editables, prerrellenados.
5. **Firma** — dibujar en Canvas o extraer de foto (locate_signature); colocación
   automática en pág. 24 con ajuste manual (sliders); PDF final relleno+firmado
   para compartir (FileProvider) o guardar (SAF).

## Arquitectura clave
- `data/model/AiModels.kt` — 9 motores; ProxyRequest/Response ALINEADOS a ai-proxy.php
  (docs[].b64/text, task, seq, gemini_mode; respuesta {ok, text}).
- `data/model/Extraction.kt` — sugerencias/alternativas/paquetes + 15 campos canónicos
  (ContractFields.CANON, con dobles espacios frágiles). Regla fija: Responsable Comercial
  MASORANGE = PABLO SALVADOR POVEDA.
- `data/remote/ExtractionPrompt.kt` — prompt VERBATIM de la web (no tocar sin replicar).
- `data/remote/AiJsonParser.kt` — extrae el JSON aunque venga envuelto en texto/```.
- `data/remote/MultiAiExtractor.kt` — fan-out por doc y motor, agrega por campo con
  consenso; Groq se limita a sugerencias (especula en texto plano).
- `data/pdf/DocumentLoader.kt` — imagen→JPEG b64; PDF→rasteriza cada página a JPEG
  (PdfRenderer nativo) para motores de visión.
- `data/pdf/AcroFormFiller.kt` — rellena el AcroForm (pdfbox-android), respeta nombres
  exactos, autofill Responsable, heurística firma pág. 24.
- `ui/wizard/*` — WizardViewModel (orquesta todo) + 5 pantallas + stepper.

## Contrato del proxy (ai-proxy.php) — CONFIRMADO
- GET → {ok, providers:{claude:true,...}}  (qué motores tienen clave)
- POST {provider, prompt, task, seq, gemini_mode, max_tokens, docs:[{mime,b64}|{text}]}
  → {ok, provider, text}
- Reduce imágenes en servidor (MAX_IMG_SIDE=1600). PDF nativo solo Claude/Gemini;
  el resto reciben imágenes (por eso rasterizamos PDF a JPEG en el cliente).

## Fase 2 (COMPLETADA)
- data/model/Signature.kt — SignatureData (PNG+aspect), SignatureStamp (pos. relativa), SignatureBox.
- data/pdf/AcroFormFiller.kt — generate(): rellena + estampa firma en coords PDF (pág. 24).
- data/pdf/SignatureProcessor.kt — recorte a caja IA + umbralización a trazo transparente.
- data/pdf/PdfExporter.kt — abre assets/contrato-base.pdf o URI usuario, genera a filesDir/output,
  FileProvider (share) + SAF CreateDocument (guardar).
- data/remote/SignatureLocator.kt — task locate_signature, orden de motores de la web.
- ui/wizard/SignatureCanvas.kt — lienzo manuscrito -> Bitmap.
- ui/wizard/SignatureStep.kt — tabs Dibujar/Extraer, ajuste con sliders, generar/compartir/guardar.
- FileProvider en manifest + res/xml/file_paths.xml.

## Fase 3 (COMPLETADA) — Paquetes en bloque
- CANON ampliado con bloque _2 (Dirección_2/CP_2/Población_2/Provincia_2 = comercio/PdV).
- data/model/Extraction.kt::PackageApplier — aplica un paquete de golpe; direcciones
  pueden ir al bloque fiscal o comercio (_2) según elija el usuario (fiel a applyPaquete web).
- WizardViewModel::applyPackage(paquete, targetBlock2).
- ui/wizard/ReviewStep.kt — sección "Bloques detectados" arriba: cada paquete se aplica
  de un toque; direcciones ofrecen "A dirección fiscal" / "A comercio (_2)".
- FillStep muestra automáticamente los 20 campos (incluye _2); AcroFormFiller los escribe
  por nombre exacto sin cambios.

## Fase 4 (COMPLETADA) — Mapeo de PDF propio + campos reales verificados
VERIFICADO con la skill pdf sobre el contrato real: 23 campos (20 texto + 3 checkbox).
Correcciones aplicadas a CANON (antes asumidos):
- DOS emails separados: "Email Comercial" (1 espacio) y "Email  Facturación" (2 espacios).
- Checkboxes NIF/CIF (valores /On /Off): se marcan solo si tipo_identificacion es CIF o NIF
  (NIE no marca). ContractFields.checkboxStateFor().
- Confirmados Dirección_2/CP_2/Población_2/Provincia_2 (bloque comercio).
- Campo residual en pág. 46 (nombre larguísimo) se ignora.
Componentes:
- data/pdf/TemplateMapper.kt — auto-mapea nombres reales del PDF del usuario a claves
  canónicas por similitud normalizada (norm() = sin acentos/minúsculas/espacios colapsados).
- AcroFormFiller.generate() — nuevos params: checkboxes (/On /Off) y fieldMapping
  (canónica->real). Verificado rellenando el contrato real con la skill.
- WizardViewModel — chooseUserContract() lee campos reales (listFields) y auto-mapea;
  setMapping() para ajuste manual; generatePdf() pasa checkboxes+fieldMapping.
- ui/wizard/MappingEditor.kt — editor de mapeo (dropdown por campo canónico).
- ContractStep — muestra el editor si el usuario aportó su PDF (needsMapping).

## PARIDAD CON WEB — plan de tandas (OK a todo del usuario)
Tanda A ✅ · B ✅ · C ✅ · D ✅ · E ✅ · F ✅ · G remates

## Tanda F (COMPLETADA) — Persistencia: plantillas, perfiles, historial
- data/model/ContractProfile.kt — objeto perfil reutilizado (campos + fingerprint + fecha),
  fiel a buildProfileObject() de la web. TemplateFingerprint.of() = nº páginas + nombres
  de campo normalizados y ordenados (fiel a templateFingerprint()).
- PrefsRepository — saveTemplate/findTemplate (mapeo por huella, DataStore+JSON),
  saveToHistory/listHistory/deleteFromHistory, exportProfileJson/importProfileJson
  (valida tipo="perfil-rellenador-pdv").
- WizardViewModel — al elegir PDF de usuario calcula huella y busca plantilla guardada
  (si existe, la usa y NO pide revisar mapeo); rememberTemplateMapping() al confirmar
  el editor; saveCurrentToHistory/loadHistoryList/applyProfile/importProfileFromJson.
- ui/wizard/HistoryPanel.kt — diálogo: guardar contrato actual con nombre, cargar o
  borrar entradas. Integrado en FillStep (botón "Historial").

## Tanda E (COMPLETADA) — Firma avanzada + arrastre
- SignatureProcessor — otsuThreshold (umbral automático), flattenIllumination (aplana
  iluminación por blur de escalado), processInk (tintado alpha-graduado + recorte a bbox
  + fondo transp/blanco), fromPhoto (pipeline completo). Todo fiel a la web.
- inkColor + sigBackground configurables (azul/negro/azul claro · transparente/blanco).
- Firmas guardadas: PrefsRepository.saveSignature/listSignatures/getSignature (DataStore);
  guardar con nombre y reutilizar en otros contratos.
- PdfPreview — ARRASTRAR la firma sobre cada página de firma para recolocarla (detectDrag
  + detectTap), marcador visual ✍ arrastrable en la posición actual del stamp.
- SignatureStep — opciones de tinta/fondo, firmas guardadas (chips), guardar firma actual.

## Tanda D (COMPLETADA) — Extracción fina
- data/model/DateAutofill.kt — autofill fecha actual (día/mes-letras-ES/año-último-dígito),
  solo campos de fecha vacíos. Fiel a autoFillDates(). YA rellena las fechas de pág.1.
- MultiAiExtractor — votación de tipo de identificación por MAYORÍA (tipoVotes, antes
  cogía el primero); corte inteligente earlyStop (allCovered() sobre campos canónicos
  sin fechas) fiel a allFieldsCovered() — deja de llamar motores si ya está todo cubierto.
- WizardViewModel.runExtraction — aplica DateAutofill.values() tras el prefill.
- ReviewStep — tap-again-to-deselect: tocar el candidato ya elegido lo desmarca (fiel web).
- 4 tests DateAutofillTest (año=1 dígito, mes español, no sobrescribe).
NOTA: las fechas de pág.1 YA se rellenan automáticamente al extraer.

## Tanda C (COMPLETADA) — Previsualización del PDF
- data/pdf/PdfPageRenderer.kt — render bajo demanda (PdfRenderer) con caché LRU (4 págs),
  para las 54 páginas sin agotar memoria.
- PdfExporter.generatePreview() — genera PDF temporal (preview.pdf) con mismo contenido
  que el final (campos + firmas estampadas).
- WizardViewModel — buildPreview() (genera + abre renderer), renderer(), moveStamp()
  (recolocar firma por toque), onCleared() libera el renderer.
- ui/wizard/PdfPreview.kt — LazyColumn de 54 páginas navegables, badge "✍ firma" en
  páginas detectadas, TOQUE en página de firma para recolocar la firma ahí.
- SignatureStep — buildPreview() al entrar (LaunchedEffect), sección de preview con
  altura FIJA 560.dp (evita crash de LazyColumn en scroll anidado), botón actualizar.
NOTA: la preview refleja el estado actual; tras cambiar firma/campos, "Actualizar
previsualización" regenera. Las fechas de pág.1 siguen vacías hasta Tanda D (autofill).

## Tanda B (COMPLETADA) — Detección real de huecos de firma
VERIFICADO contra el contrato real con pdfplumber/pypdf:
- Huecos de firma reales = páginas 24, 30, 33, 45, 54 (NO solo la 24 fija).
- Algoritmo: campos multipágina (Fecha/de/año repetidos en esas páginas, excluyendo
  portada) CRUZADO con presencia del rótulo "EL DISTRIBUIDOR" (señal fuerte) + su Y.
- Lección clave: los tokenizadores de texto varían (la pág 24 se detecta distinto según
  el método), por eso SIEMPRE se permite añadir/quitar páginas manualmente.
Componentes:
- data/pdf/SignaturePageDetector.kt — pdfbox: lee widgets multipágina + PDFTextStripper
  que localiza "DISTRIBUIDOR" y su Y por página. Fallback a todas las multipágina.
- WizardViewModel — detectSignaturePages() (auto al elegir contrato), addSignPage/
  removeSignPage, stampAllPages (masivo), stampOnePage (una a una), ancla bajo rótulo.
- SignatureStep — lista de páginas detectadas con quitar/colocar, añadir página manual,
  botón "Firmar todas las páginas (N)".
- generatePdf ya estampa en TODAS las páginas de state.stamps (multipágina).

## Tanda A (COMPLETADA) — Validación + normalización
- data/validation/SpanishValidators.kt — DNI/NIE/CIF (control), IBAN (mod-97), teléfono,
  email, día. Algoritmos VERBATIM de la web, VERIFICADOS con valores reales
  (DNI 12345678Z ✓, CIF A82528548=Xfera ✓, IBAN ES91... ✓).
- data/validation/FieldNormalizer.kt — normVal (IBAN/CP/NIF/nombre "Apellidos, Nombre"->"Nombre Apellidos")
  + tabla PROV (52 provincias) + cpProvinciaMsg.
- data/validation/FieldValidator.kt — valida por campo canónico (fiel a validateField).
- MultiAiExtractor — normaliza cada valor con normVal antes de agregar.
- FillStep — validación en vivo bajo cada campo + teclado adecuado por tipo.
- 10 tests unitarios con casos reales (ValidatorsTest.kt).

## Fixes post-Tanda F (bugs reales reportados en uso)
- **Fotos sin redimensionar → 400/500/429**: DocumentLoader mandaba fotos del cliente
  a resolución completa (varios MB). Causaba Claude 400, Gemini 500, Groq/Mistral 429/
  incompleta. FIX: downscaleIfNeeded() a 1600px lado mayor antes de enviar (igual que
  ya se hacía para páginas de PDF rasterizadas).
- **locate_signature con el mismo problema**: extractSignatureFromPhoto también mandaba
  el bitmap sin redimensionar. Mismo fix aplicado (1600px antes de base64).
- **Preview de firma ausente**: SignatureStep no mostraba la imagen de la firma procesada,
  solo un chip de texto. FIX: Image() con el PNG decodificado (recorte/tinta/fondo ya
  aplicados) antes del chip "Firma preparada".
- **Arrastre de firma interfería con el scroll**: el gesto respondía en toda la página,
  no solo en el marcador, y podía cancelarse a mitad de arrastre. FIX: el gesto vive
  SOLO en el marcador ✍; primer toque = seleccionar (resalta), con seleccionado=true
  se puede arrastrar (acumulando posición localmente para no perder el gesto).
- **Dos commits "Tanda F" distintos en el repo**: c3b6545 (código huérfano de Ajustes/
  Historial de una fase muy anterior, nunca conectado) vs el actual (ContractProfile/
  HistoryPanel). Si persisten errores de compilación en ui/history o ui/settings,
  hay que `git rm -r` esas carpetas — no son parte de esta migración actual.

## Tanda Ajustes (COMPLETADA) — perfil comercial, URL proxy, motores persistidos
- PrefsRepository: responsableComercial (Flow, default "PABLO SALVADOR POVEDA") +
  setResponsableComercial(); proxyBaseUrlOverride (Flow, default "") + setProxyBaseUrlOverride().
- data/remote/DynamicBaseUrlInterceptor.kt — interceptor OkHttp: si hay URL override
  guardada, reescribe host/base de cada petición manteniendo el endpoint (ai-proxy.php)
  y la query; si no hay override, la petición sale con BuildConfig.PROXY_BASE_URL tal cual.
  Lectura de DataStore vía runBlocking (corre en hilo de OkHttp, no en el principal).
- AppModule: OkHttpClient ahora inyecta el interceptor dinámico antes del logging.
- WizardState: responsableComercial + proxyBaseUrlOverride. WizardViewModel:
  loadPersistedSettings() al iniciar (carga perfil y URL guardados); probeProviders()
  ahora cruza los motores disponibles con los persistidos en prefs.enabledProviders
  (si el usuario ya eligió antes, se respeta esa selección); toggleProvider() ahora
  persiste con prefs.setEnabled() además de actualizar el state; prefill del contrato
  usa state.responsableComercial (editable) en vez de la constante fija.
- ui/settings/AjustesScreen.kt (NUEVO, no confundir con el huérfano ya borrado):
  editar nombre del responsable, URL del proxy (+ restaurar por defecto), switches
  de motores IA activos (persistidos).
- Navegación: RellenadorNavHost tiene ruta "ajustes"; comparte la MISMA instancia de
  WizardViewModel entre "wizard" y "ajustes" (vía hiltViewModel(backStackEntry) del
  backstack de "wizard") para que los cambios se vean sin recargar. Icono de Ajustes
  en el TopAppBar del WizardScreen.
- FillStep muestra el nombre real configurado (no la constante) en el chip automático.

## FUSIÓN con trabajo paralelo detectado (commit 8d611c5, "v0.2.2-stamp-letterbox")
Se detectó trabajo de OTRA sesión/herramienta (probablemente Claude Code en el propio
Codespace, a juzgar por el CHANGELOG.md y los diffs quirúrgicos) directamente sobre el
repo, en paralelo a esta conversación. Incluía una versión intermedia "0.2.1-firma-fix"
de la que no hay rastro, y "0.2.2-stamp-letterbox" (commit 8d611c5), fusionada aquí:

- **Signature.kt**: SignatureStamp ganó `heightRel` (antes solo `widthRel`; la altura se
  derivaba del aspect ratio de la firma, deformando/recortando en el hueco real).
- **AcroFormFiller.generate()**: encaje tipo "letterbox" — calcula una caja
  (widthRel×heightRel) y escala la firma DENTRO de ella preservando su propio aspect
  ratio (sin deformar), centrada. Antes: `h = w * signature.aspectRatio` (incorrecto).
- **WizardViewModel**: `calibratedStamps`/`stampFor()` — coordenadas REALES calibradas
  con pdfplumber contra un contrato ya firmado (`contrato-relleno-a1.pdf`) para las 5
  páginas de firma (24, 30, 33, 45, 54). Descubrimiento importante: en páginas 30 y 33
  el bloque "EL DISTRIBUIDOR" está a la DERECHA, no a la izquierda. Reemplaza la
  heurística genérica (ancla+0.06, x=0.30 fijo) que antes usaban detectSignaturePages/
  stampAllPages/stampOnePage. updateStamp() y moveStamp() ahora preservan heightRel.
- **IMPORTANTE — .github/workflows/android.yml**: el repo real tiene un workflow
  modificado (extrae versionName de build.gradle.kts, nombra el APK/artefacto con él)
  que Claude NUNCA ha tocado. Los ZIPs de Claude EXCLUYEN este archivo a propósito
  (con `-x` en el zip) para no sobrescribirlo. Si en el futuro hace falta tocar el
  workflow, pedir a Pablo que pegue su contenido actual primero.
- versionCode/versionName bumped a 5 / "0.3.0-ajustes-letterbox" para reflejar la fusión.
- Lección para el futuro: verificar SIEMPRE con `git log --oneline --all` si hay commits
  no reconocidos antes de generar un ZIP completo nuevo, para no repetir este problema.

## Pendiente (siguiente tanda) — [HISTÓRICO: todo lo de esta lista está HECHO desde hace meses; ver CHANGELOG]
- **Firma**: captura manuscrita en Canvas + task "locate_signature" (ya soportada por
  el proxy) para ubicar el hueco + inserción en página 24 + generar PDF final con
  AcroFormFiller + compartir/guardar (FileProvider).
- **Paquetes**: aplicar paquetes (dirección fiscal / comercio / empresa / persona / banco)
  con un solo toque en Revisión, incluyendo el mapeo a bloques _2. El modelo ya los
  captura (state.packages); falta la UI de aplicación en bloque.
- **Ajustes**: pantalla para editar perfil comercial y URL del proxy; persistir motores
  elegidos y plantillas mapeadas en PrefsRepository.
- **Release firmado**: signingConfig + keystore + APK release en el workflow.

## Notas técnicas heredadas
- Kotlin 2.1 requiere plugin org.jetbrains.kotlin.plugin.compose (ya en ambos build.gradle.kts).
- gradlew real + gradle-wrapper.jar presentes; workflow con android-actions/setup-android.
- El icono adaptativo vive en mipmap-anydpi-v26 (minSdk 26).
