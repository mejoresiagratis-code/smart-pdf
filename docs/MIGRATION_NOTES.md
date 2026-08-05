# Notas de migración web → Android

| Web (single-file JS)        | Android nativo                          |
|-----------------------------|-----------------------------------------|
| `rellenador-pro.html`       | Compose UI + ViewModel                  |
| `ai-proxy.php` (se mantiene)| `ProxyApi` + `MultiAiExtractor`         |
| `localStorage`              | `DataStore` (`PrefsRepository`)         |
| pdf-lib (AcroForm)          | `pdfbox-android` (`AcroFormFiller`)     |
| bucle multi-motor en JS     | `coroutineScope { async { } }` fan-out  |
| CSP / no-CDN / GDPR barrier | `network_security_config` HTTPS-only    |
| `pdfTextOf()` (texto de PDF, solo Groq) | `DocumentLoader.firstPagesText()` (PDFBox) — hoy tipifica el documento; reutilizable para mandar texto a motores |
| — (sin equivalente web)     | `DocumentTypeDetector` (tipo por contenido) + `tipo_documento` por IA (v0.7.8/0.7.9) |
| DNS del navegador (Happy Eyeballs) | `Ipv4PreferredDns` (IPv4 primero + fallback DoH, v0.7.5/0.7.10) |

## Estrategia de merge multi-IA
Igual que la web: se consultan los motores activos en paralelo y se fusiona por
nombre de campo; gana la mayor confianza, empates por orden de proveedor.

## Riesgos conocidos
- `pdfbox-android` requiere `PDFBoxResourceLoader.init()` en `Application`
  (ya incluido). Los *appearances* se fuerzan con `needAppearances = true`.
- El `flatten()` debe ser opcional: solo tras la firma final, no antes.

## Paridad de prompt (regla viva)
`ExtractionPrompt.kt` debe ser VERBATIM con el prompt de `rellenador-pro.html`.
Divergencia pendiente a 2026-08-05: el campo `tipo_documento` (v0.7.9, Android)
aún no está replicado en la web.
