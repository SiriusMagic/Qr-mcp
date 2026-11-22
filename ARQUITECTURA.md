# Arquitectura de QR

Carpeta = package (`android/app/src/main/java/com/zirakusa/qr/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | Lógica pura, con pruebas. `Payload` (`Content`: texto, enlace, Wi‑Fi, contacto MECARD/vCard, teléfono, correo, SMS, lugar; leer, construir con escapes, `Problem` de un formulario y título corto); `LinkCheck` (sitio real y `Warning`s sin abrir nada); `History` (`Entry`, `Source`, `Filter`; JSON en las preferencias «qr», sin repetir lo que la cámara ve varias veces, 500 como mucho); `Codes` (ZXing: dibujar un QR como `Grid`, leer un fotograma o una imagen, girar para códigos de barras de lado, PNG propio); `Settings` y `KeyValues`. |
| `ui/` | `QrState` (único dueño del estado: pestaña, página, historial, el código mostrado, el formulario de «Crear» y ajustes) y `QrApp` (pestañas Escanear · Crear · Historial con la barra de abajo, páginas encima; `AppActions`). |
| `ui/screens/` | `ScanScreen`, `CreateScreen`, `HistoryScreen`, `ResultScreen` (sitio real, avisos, campos, acción y botones), `SettingsScreen`, `McpScreen` (igual que en las apps hermanas), `Parts` (`QrImage`, iconos), `Chrome` y `SettingsParts`. Solo pintan y avisan. |
| `ui/theme/` | `Theme`: `ThemeMode` (Sistema, Claro, Oscuro, Negro de fábrica), `Accent` (azul de fábrica…) y `QrColors`. El QR se dibuja siempre negro sobre blanco. |
| `mcp/` | Modo MCP, como las apps hermanas: `Tokens` (`qrc_…`, cifrados con `SecretBox`), `QrTools` (`qr_history`, `qr_item`, `qr_parse`, `qr_make`; con escritura `qr_create`, `qr_delete`; a través de `QrData`; `ToolOutput` lleva la imagen PNG), `McpHandler`, `RelayLink` (relay de Stone), `McpMode` (`LiveData`) y `HubLink` (módulo `qr_`). |
| `Camera.kt` | `CameraView`: CameraX (vista previa y análisis del último fotograma en un hilo) → `Codes.readFrame`. |
| `Openers.kt` | Lo que hace cada botón: abrir enlace, marcar, correo, SMS, añadir contacto, mapa, Wi‑Fi (hoja «Guardar red» de Android 11+), copiar, compartir PNG (`FileProvider`, solo `cache/share`) y guardar en Imágenes › QR. |
| `Summary.kt` | `SummaryLines` (pura, con prueba) y `SummaryProvider`: «Códigos recientes» para Inicio (`FAMILIA.md`), solo con la misma firma. |
| `MainActivity.kt` | Crea el estado, pide la cámara, lee imágenes (selector de fotos), vibra, copia e «Ir al Hub». |

Reglas:
- Texto visible en `res/values/strings.xml` (español) y `values-en`.
- Colores siempre de `QrColors` (más `Amber` para avisos, en `ResultScreen`).
- Archivos de pantalla de unas 150 líneas como mucho; si crece, se divide.
