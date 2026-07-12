# Logos oficiales pendientes de sustituir (Tanda 2)

Los 9 drawables en `app/src/main/res/drawable/ic_provider_*.xml` son placeholders
(un disco relleno con el color de marca) generados en la Tanda 2. Compilan y se ven
bien desde ya, pero **no son los logos oficiales**. Para sustituirlos:

1. Descarga el SVG/PNG oficial desde el brand kit correspondiente (enlaces abajo).
2. Conviértelo a Vector Drawable de Android:
   - Android Studio: clic derecho en `res/drawable` → New → Vector Asset → "Local file (SVG, PSD)".
   - O con la herramienta online https://svg2vectordrawable.com/ y pegar el XML resultante.
3. **Guarda el archivo con el MISMO nombre** que ya existe (ej. `ic_provider_claude.xml`).
   No hace falta tocar ningún `.kt` — `ProviderLogo` carga el recurso por nombre
   (`AiProvider.drawableName`), así que el cambio es automático.
4. Si el logo oficial ya incluye texto/wordmark y no quieres la inicial superpuesta,
   pásale `showInitial = false` a `ProviderLogo` en el call site correspondiente
   (`EngineChip`, `MotorLoadingIndicator`).

## Brand kits oficiales

| Proveedor | Recurso | Brand kit |
|---|---|---|
| Claude (Anthropic) | `ic_provider_claude.xml` | https://www.anthropic.com/brand |
| Gemini (Google) | `ic_provider_gemini.xml` | https://ai.google.dev/gemini-api/docs/brand-guidelines |
| Groq | `ic_provider_groq.xml` | https://groq.com/press-kit/ |
| Grok (xAI) | `ic_provider_grok.xml` | https://x.ai/brand |
| Mistral | `ic_provider_mistral.xml` | https://mistral.ai (assets de marca en el footer / prensa) |
| Scaleway | `ic_provider_scaleway.xml` | https://www.scaleway.com/en/press-kit/ |
| OVHcloud | `ic_provider_ovh.xml` | https://corporate.ovhcloud.com/en/newsroom/media-library/ |
| Nebius | `ic_provider_nebius.xml` | https://nebius.com/press-kit |
| EUrouter | `ic_provider_eurouter.xml` | (servicio propio/agregador — sin brand kit externo; puedes diseñar tú el icono o dejar el glyph) |

## Nota legal
El uso nominativo de un logo para identificar el servicio real que procesa un
documento (ej. "Claude está analizando...") es una práctica habitual en apps que
integran varios proveedores de IA. Aun así, usa el asset oficial tal cual lo
proporciona cada marca (sin recolorear, deformar ni combinar con otros elementos)
y evita implicar patrocinio o asociación oficial con tu app.
