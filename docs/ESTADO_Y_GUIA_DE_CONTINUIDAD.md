# Estado y guía de continuidad — Rellenador Android (nativo)

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

## Pendiente (siguiente tanda)
- **Firma**: captura manuscrita en Canvas + task "locate_signature" (ya soportada por
  el proxy) para ubicar el hueco + inserción en página 24 + generar PDF final con
  AcroFormFiller + compartir/guardar (FileProvider).
- **Paquetes**: aplicar paquetes (dirección fiscal / comercio / empresa / persona / banco)
  con un solo toque en Revisión, incluyendo el mapeo a bloques _2. El modelo ya los
  captura (state.packages); falta la UI de aplicación en bloque.
- **Ajustes**: pantalla para editar perfil comercial y URL del proxy; persistir motores
  elegidos en PrefsRepository.
- **Plantillas / detección de PDF**: mapear nombres reales del AcroForm a claves canónicas
  cuando el usuario aporta su propio PDF (detectTemplate de la web).
- **Release firmado**: signingConfig + keystore + APK release en el workflow.

## Notas técnicas heredadas
- Kotlin 2.1 requiere plugin org.jetbrains.kotlin.plugin.compose (ya en ambos build.gradle.kts).
- gradlew real + gradle-wrapper.jar presentes; workflow con android-actions/setup-android.
- El icono adaptativo vive en mipmap-anydpi-v26 (minSdk 26).
