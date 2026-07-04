# Notas de migración web → Android

| Web (single-file JS)        | Android nativo                          |
|-----------------------------|-----------------------------------------|
| `rellenador-pro.html`       | Compose UI + ViewModel                  |
| `ai-proxy.php` (se mantiene)| `ProxyApi` + `MultiAiExtractor`         |
| `localStorage`              | `DataStore` (`PrefsRepository`)         |
| pdf-lib (AcroForm)          | `pdfbox-android` (`AcroFormFiller`)     |
| bucle multi-motor en JS     | `coroutineScope { async { } }` fan-out  |
| CSP / no-CDN / GDPR barrier | `network_security_config` HTTPS-only    |

## Estrategia de merge multi-IA
Igual que la web: se consultan los motores activos en paralelo y se fusiona por
nombre de campo; gana la mayor confianza, empates por orden de proveedor.

## Riesgos conocidos
- `pdfbox-android` requiere `PDFBoxResourceLoader.init()` en `Application`
  (ya incluido). Los *appearances* se fuerzan con `needAppearances = true`.
- El `flatten()` debe ser opcional: solo tras la firma final, no antes.
