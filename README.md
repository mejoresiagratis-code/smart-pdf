# Rellenador de Contratos — Android (nativo)

Migración nativa de la web app *Rellenador de Contratos PdV* a Android
(Kotlin + Jetpack Compose, Material 3 Expressive). Rellena, valida y firma el
AcroForm de 54 páginas de los contratos de distribución Orange/MASORANGE, con
extracción multi-IA de los documentos del distribuidor (DNI/NIE, tarjeta CIF,
certificado censal, Modelo 036, certificado bancario, contrato de alquiler,
escrituras, alta en RETA…).

**Versión actual:** `0.10.4-editor-cableado` (versionCode 74) — ver `CHANGELOG.md`.

## Flujo (4 pasos)
1. **Contrato** — el de assets (`contrato-base.pdf`) o un PDF del usuario
   (con auto-mapeo de campos por similitud y editor de mapeo manual).
   Muestra la "Estructura detectada" (páginas, campos, huecos de firma).
2. **Documentación** — fotos/PDFs del cliente + selección de motores IA.
3. **Relleno** — el formulario llega **ya prerrellenado por la IA** (desde v0.8.0
   absorbe la antigua "Revisión IA"). Cada campo muestra su estado —autorrellenado,
   en conflicto o de procedencia dudosa— y **de qué documento salió**. Los conflictos
   se resuelven en una hoja inferior con las alternativas y su origen; nada que sea
   dudoso se rellena solo, y bloquea el avance hasta decidirlo. Validación en vivo
   (DNI/NIE/CIF con dígito de control, IBAN mod-97, CP↔provincia) y deshacer.
4. **Firma** — dibujar, extraer de foto (con IA `locate_signature`, borrador y
   recorte manual) o reutilizar guardadas; estampado calibrado en las 5 páginas
   de firma reales (24, 30, 33, 45, 54); previsualización navegable de las 54
   páginas; PDF final para compartir o guardar.

## Stack
- **Kotlin 2.1** + **Jetpack Compose** · **Material 3 Expressive**
  (`material3:1.4.0-alpha16` — no subir a 1.5.x: rompe con compileSdk 35/AGP actual)
- **Hilt** (DI) · **Retrofit/OkHttp** + **kotlinx.serialization**
- **pdfbox-android** (tom-roush): rellenar AcroForm, detectar páginas de firma
  y extraer texto (tipificación de documentos por contenido)
- **DataStore**: sesión del wizard, motores, plantillas, historial, firmas
- **minSdk 26** · **targetSdk 35** · CI en GitHub Actions (APK debug con el
  `versionName` en el nombre del artefacto)

## Arquitectura
```
ui/wizard/       WizardScreen + 4 pasos + WizardViewModel (orquestador)
ui/components/   ExpressiveAccordion, ProviderLogo, diálogos de análisis
data/model/      AiProvider (9 motores), Extraction (CANON 22 campos, paquetes),
                 FieldResolver + AutoFillPolicy (autorrelleno por procedencia),
                 Signature, ContractProfile, DateAutofill
data/remote/     ProxyApi, MultiAiExtractor (fan-out+merge+earlyStop),
                 ExtractionPrompt (VERBATIM web), AiJsonParser,
                 SignatureLocator, Ipv4PreferredDns (+fallback DoH)
data/pdf/        AcroFormFiller, SignaturePageDetector, DocumentLoader,
                 DocumentTypeDetector (tipo por contenido), PdfPageRenderer,
                 SignatureProcessor, PdfExporter, TemplateMapper
data/validation/ SpanishValidators, FieldNormalizer, FieldValidator
data/repository/ PrefsRepository (DataStore)
di/              AppModule (Retrofit, OkHttp+DNS, Json encodeDefaults)
```

## El proxy sigue siendo el guardián de las claves
La app llama a `ai-proxy.php` (producción: `datingtrck.com/pdf/`, configurable
en Ajustes; URL de fábrica en `PROXY_BASE_URL` de `app/build.gradle.kts`). Las
claves de las IAs **no viajan en el binario**. El fichero del proxy NO vive en
este repo — se despliega por FTP/cPanel, con su config en `proxyconfig/`
protegida por `.htaccess`.

## Cosas que se mantienen del proyecto web (no romper)
- Nombres de campo del AcroForm **exactos y frágiles**: los dobles espacios
  importan (`Nombre  Razón Social`, `Email  Facturación`).
- Auto-relleno de `Responsable Comercial MASORANGE` = `PABLO SALVADOR POVEDA`.
- **Página 24**: sin campos AcroForm → hueco de firma force-incluido.
- Checkbox del tipo NIE se llama literalmente `"undefined"` en el PDF real.
- `ExtractionPrompt` es **verbatim de la web**: cualquier cambio hay que
  replicarlo en la app web para mantener la paridad (pendiente actual:
  `tipo_documento`, añadido en v0.7.9).

## Puesta en marcha
1. `contrato-base.pdf` ya está en `app/src/main/assets/`.
2. Ajusta `PROXY_BASE_URL` en `app/build.gradle.kts` si cambia el host.
3. `./gradlew assembleDebug` — o deja que el CI genere el APK en cada push.

## Documentación del repo
- `CHANGELOG.md` — historial completo por versión.
- `ROADMAP.md` — estado real + próximas tandas.
- `LOGOS_TODO.md` — sustitución de logos placeholder por oficiales.
- `docs/ESTADO_Y_GUIA_DE_CONTINUIDAD.md` — guía de arranque de sesión + archivo
  histórico de tandas.
- `docs/MIGRATION_NOTES.md` — equivalencias web → Android.
