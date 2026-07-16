# Roadmap — Rellenador de Contratos PdV (Android)

Estado real del proyecto y próximas tandas planificadas. Este documento sustituye al
"roadmap" informal que vivía en las notas de continuidad de las sesiones. Se actualiza
al final de cada tanda con lo que quede pendiente.

Última actualización: **2026-07-15** (versión `0.6.5-persistencia-sesion`).

---

## Completado (versiones con build verde)

| Versión | Contenido |
|---|---|
| **0** | Fundación: paleta de color desde el naranja de marca, `MotionScheme.expressive()`, terciario azul-verdoso. |
| **1** | Wizard shell: TopAppBar con `primaryContainer`, stepper de 3 estados, `LoadingIndicator` Expressive. |
| **Mockup Contrato + Ajustes** | Formas "blob", `ContractOptionCard`, `TipBanner`, Ajustes como `ModalBottomSheet`. |
| **2** | Documentación: chips de motor con logo real (`ProviderLogo`/`ProviderGlyph`), `MotorLoadingIndicator` contextual, callbacks `onProviderStart/Finish` en el extractor. |
| **3** | Revisión IA + Relleno: secciones con `surfaceContainer`, tipo de identificación NIF/CIF/NIE editable, progreso real, "copiar fiscal", fecha compacta, chips de candidato con logo, formas unificadas. |
| **0.5.6** | `Actividad principal del negocio` + `Profesión...` en `CANON`, checkbox NIE (`undefined`) arreglado, prompt de extracción reforzado. |
| **0.5.7** | Documentación con blob hero + acordeones plegables + progreso en vivo documento×motor. |
| **0.5.8** | Motion physics real (MotionScheme del tema), formas diferenciadas entre secciones, contadores animados. |
| **0.5.9** | Firma: `TabRow` → `SegmentedButton`, quitado campo `Profesión` sin uso. |
| **0.6.0** | Blob como CTA, pop-up modal para progreso, títulos "Paso N..." retirados en los 5 pasos, regla del prompt para DNI+CIF combinado. |
| **0.6.1** | Doble pop-up arreglado (Documentación tiene su Dialog rico, otros pasos usan el genérico). Pantalla de Documentación rellena todo el espacio sin huecos. Pop-up rico limpio (sin círculos redundantes). |
| **0.6.2** | Contexto de conjunto de documentos para el prompt (arregla clasificación errónea de DNI/NIE aislado como autónomo cuando hay CIF en el conjunto). Pop-up con `LoadingIndicator` squiggly de M3 Expressive arriba, en vez de logo duplicado. |
| **0.6.3** | Firma alineada con web: `ExpressiveAccordion` compartido, "Ajustes de firma" y "Huecos de firma" plegables, "Una a una" + "⚡ Todos", paleta ampliada a 6 tintas, checkbox "Mejorar con IA", botón "📷 Hacer foto" con permiso `CAMERA` en Manifest. |
| **0.6.4** | Firma: previsualización sin duplicados, scroll animado a página estampada, snackbar de feedback, flechas de navegación entre huecos como en la web. |
| **0.6.5** | **Persistencia de sesión (Fase 1)**: el progreso del wizard sobrevive al segundo plano y a la muerte del proceso. Botón "Empezar de nuevo" en Ajustes y "Empezar otro contrato" tras generar el PDF. Aviso si un URI restaurado ya no es accesible. |

---

## Próximas tandas

### 🔴 Alta prioridad

- **Persistencia de documentos (Fase 2 de robustez)**
  Copiar los documentos aportados a almacenamiento privado de la app al añadirlos (o
  llamar a `takePersistableUriPermission` cuando sea posible con el picker usado). Hoy,
  si el proceso muere en segundo plano, los `Uri`s persistidos pueden volverse inaccesibles
  y el usuario tiene que volver a añadir los documentos (con un aviso claro, eso sí).
  Esta tanda lo resuelve del todo copiando los bytes a `getExternalFilesDir` o
  `context.filesDir` — un directorio propio de la app que no depende del proveedor del URI.
  *Fichero clave*: `DocumentLoader.kt`, `WizardViewModel.addDocuments`, `WizardUiState.docUris`.

- **Subir `ai-proxy.php` corregido a producción**
  El proxy real en `mejoresiagratis.com/pdf/ai-proxy.php` puede seguir con los modelos
  antiguos (`gemini-2.5-flash`, `mistral-small-latest` para EUrouter). Sin este paso,
  Gemini y EUrouter pueden fallar todas las extracciones reales aunque el APK compile
  perfecto. El fix está entregado en las últimas ZIPs — solo falta subirlo por FTP/cPanel.

### 🟠 Media prioridad

- **Modo "De documento" en firma**
  Añadir el 3.º tab del paso de Firma (Web ya lo tiene): permite elegir un PDF de los que
  se subieron en el Paso 2 y recortar la firma directamente de ahí. Implica enlazar
  `docUris` del Paso 2 con un selector/recorte en el Paso 5. Es una tanda pequeña propia.

- **APK release firmado**
  Hoy el workflow solo compila `debug`. Falta añadir `signingConfig` con un keystore
  (guardado como secreto en el workflow) y un job/step separado que compile `release`
  para distribuir el APK como instalable "real" en dispositivos que verifiquen firma.

- **Persistencia exportar/importar perfil**
  El backend ya existe (`PrefsRepository.exportProfileJson` / `importProfileJson`), pero
  no hay UI en Ajustes que lo exponga. Solo hay que añadir dos botones y sus launchers.

### 🟡 Media/baja prioridad

- **Logos oficiales de proveedor**
  Hoy son placeholders (círculo con inicial + `brandColor`). El fichero `LOGOS_TODO.md`
  del repo tiene el listado de los SVGs oficiales que hay que meter en
  `res/drawable/ic_provider_*.xml`. Cuando se hagan, `ProviderLogo` los prefiere
  automáticamente sobre el placeholder.

- **`responseSchema` estricto para Gemini**
  Gemini Pro sugirió pasar a JSON estructurado nativo (`responseMimeType: application/json`
  ya se aplica; el `responseSchema` completo no). Rechazado por ahora porque requiere que
  todos los motores (Claude, Groq, Mistral, EUrouter) devuelvan el mismo formato o mantener
  dos parsers. Cuando toque hacer un refactor de `AiJsonParser` unificando el formato de
  todos los motores, este cambio entra a la vez.

- **PDF nativo a Gemini/Claude (no rasterizado)**
  Gemini y Claude aceptan PDF nativo como `inline_data`, pero hoy el cliente Android
  rasteriza SIEMPRE cada página con `PdfRenderer` antes de mandar. Si en algún momento se
  ve que la extracción pierde precisión por no ver la estructura del PDF completa (por
  ejemplo, en documentos con tablas o formularios), esta tanda migra a doble ruta: PDF
  nativo para los motores que lo soportan, JPEG rasterizado para los demás.

### 🟢 Baja prioridad (deferred de siempre)

- **Pinch-to-zoom en la miniatura de firma**
- **Controles de posición del sello por página individual** (hoy solo la página 24 tiene
  sliders; el resto se calcula con `stampFor` desde los anchors calibrados)
- **Slider de tamaño global de firma** — descartado en la tanda 0.6.3 porque requeriría
  refactor mayor de `stampFor()` para introducir un multiplicador global.

---

## Pendiente de decisión de Pablo

- **`"Profesión puestos de trabajo..."`** — descartado por Pablo en la tanda 0.5.9. Si
  algún día se necesita, la única acción es volverlo a añadir a `ContractFields.CANON`
  y a la sección Empresa/Identificación de `FillStep.kt`. El campo existe en el AcroForm
  real del contrato, solo estaba sin uso claro.

---

## Aprendizajes técnicos preservados

Estos son errores concretos que ya nos costaron una tanda de build rojo — no repetirlos:

- `MaterialTheme.motionScheme` es `@Composable`, **no se puede llamar dentro de
  `LaunchedEffect`** (suspend). Capturar en variable antes.
- `Icons.Outlined.Cpu` **no existe** en Material Icons. Usar `Icons.Outlined.Memory`.
- Gemini `thinkingLevel` y `thinkingBudget` son **mutuamente excluyentes** (mandar los dos
  da HTTP 400).
- Gemini 3.x tiene thinking activo por defecto — se come tokens de salida. Poner
  `thinkingLevel: "low"` y subir `maxOutputTokens` a 8192 mínimo.
- Página 24 del contrato **no tiene AcroForm fields** — hay que force-includirla con
  `applyKnownContractFixes()` o el detector no la ve.
- Nombres de campo con **doble espacio importan**: `"Nombre  Razón Social"`,
  `"Email  Facturación"`. `norm()` los preserva, no se debe "limpiar" ese espacio doble.
- Checkbox del tipo de identificación NIE se llama literalmente `"undefined"` en el
  AcroForm real (bug del PDF original). Ya está manejado como `CHECKBOX_NIE = "undefined"`.
- `unzip -o` **no borra archivos**, solo sobrescribe. Cualquier eliminación de fichero
  hay que hacerla con `git rm` explícito.
- Web fetch a GitHub **puede devolver cache antiguo**. Verificar HEAD real vía MCPGIT
  antes de asumir el estado del repo.
