# Rellenador de Contratos — Android (nativo)

Migración nativa de la web app *Rellenador de Contratos PdV* a Android
(Kotlin + Jetpack Compose). Rellena, valida y firma el AcroForm de 54 páginas
de los contratos de distribución Orange/MASORANGE, con extracción multi-IA.

## Stack
- **Kotlin 2.1** + **Jetpack Compose** (Material 3)
- **Hilt** (DI) · **Retrofit/OkHttp** + **kotlinx.serialization**
- **pdfbox-android** (tom-roush) para rellenar el AcroForm
- **DataStore** en vez de `localStorage` (motores activos, perfil, histórico)
- **minSdk 26** · **targetSdk 35**

## Arquitectura
```
ui/            Compose (HomeScreen, ViewModel, tema, navegación)
data/model/    AiProvider (9 motores), ProxyRequest/Response, ExtractedField
data/remote/   ProxyApi (Retrofit) + MultiAiExtractor (fan-out + merge)
data/pdf/      AcroFormFiller (rellenado + heurística firma pág. 24)
data/repository/ PrefsRepository (DataStore)
di/            AppModule (Retrofit, OkHttp, Json)
```

## El proxy sigue siendo el guardián de las claves
La app llama a `ai-proxy.php` en `PROXY_BASE_URL` (BuildConfig). Las claves de
las 9 IAs **no viajan en el binario** — igual que en la web. Si algún día
quieres ir sin proxy, cifra las claves con `EncryptedSharedPreferences`, nunca
en texto plano.

## Cosas que se mantienen del proyecto web (no romper)
- Nombres de campo del AcroForm **exactos y frágiles**: los dobles espacios
  importan (`Nombre  Razón Social`, `Email  Facturación`).
- Auto-relleno de `Responsable Comercial MASORANGE` = `PABLO SALVADOR POVEDA`.
- **Página 24**: sin campos AcroForm → hueco de firma detectado por heurística
  de campo con nombre, no por detección estructural.
- Reglas de extracción (Dirección = fiscal/AEAT, sufijo `_2` solo si dirección
  comercial distinta explícita, autónomo vs CIF) → llevar al *prompt* del proxy.

## Puesta en marcha
1. Coloca `contrato-base.pdf` en `app/src/main/assets/`.
2. Ajusta `PROXY_BASE_URL` en `app/build.gradle.kts` si cambia el host.
3. `./gradlew assembleDebug`
4. El CI de GitHub genera el APK debug como artefacto en cada push.

## Pendiente (TODO marcados en el código)
- Selector de PDF (SAF) → extracción de texto → `vm.runExtraction()`.
- Captura de firma (Canvas) e incrustado en el PDF.
- Histórico de contratos/firmas y multi-firmante.
- Pantalla de ajustes para activar/desactivar motores y editar el perfil.
