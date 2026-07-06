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
Tanda A ✅ · B ✅ · C preview · D extracción-fina · E firma-avanzada · F persistencia · G remates

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

## Pendiente (siguiente tanda)
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
