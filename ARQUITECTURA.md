# Arquitectura de QR

Carpeta = package (`android/app/src/main/java/com/zirakusa/qr/`):

| Carpeta | Qué hay |
|---|---|
| `data/` | Lógica pura, con pruebas. `Payload` (`Content`: texto, enlace, Wi‑Fi, contacto MECARD/vCard, teléfono, correo, SMS, evento VEVENT (`moment` lee fechas escritas), lugar; leer, construir con escapes, `Problem` de un formulario y título corto); `LinkCheck` (sitio real y `Warning`s sin abrir nada); `History` (`Entry`, `Source`, `Filter`; JSON en las preferencias «qr», sin repetir lo que la cámara ve varias veces, «Guardar duplicados» o subir el repetido, `import`, 500 como mucho); `HistoryCsv` (exportar e importar CSV: comillas, comas, saltos de línea, «'» ante fórmulas, columnas de otras apps); `Barcodes` y `BarFormat` (EAN‑13, EAN‑8, UPC‑A con dígito de control, Code 128, Code 39); `ScanGate` («Escaneo continuo»: un código en vista cuenta una vez); `Codes` (ZXing: dibujar un QR como `Grid` y un código de barras como `Bars`, `picture` para compartir, leer un fotograma o una imagen, girar para códigos de barras de lado, PNG propio); `Settings` y `KeyValues`. `VCard` (`Detail`, `SharedContact`: qué datos se eligen, vCard 3.0 con escapes, `Problem`, `Phones.clean`) y `ArtLayout` (dónde van el árbol, el panel blanco y el código en la tarjeta «Arbolito»: el árbol nunca toca el código). |
| `ui/` | `QrState` (único dueño del estado: pestaña, página, historial, el código mostrado, el que espera «Usar» (`Pending`, `Step`), la nota del escaneo continuo, el formulario de «Crear» (QR o código de barras), `prefill` para texto compartido o del portapapeles, y ajustes) y `QrApp` (barra Escanear · Crear · Historial · Ajustes, páginas encima; `AppActions`). |
| `ui/screens/` | `ScanScreen` y `ScanParts` (tarjeta «Usar código», nota del escaneo continuo), `CreateScreen` y `CreateFields` (campos, evento, código de barras y sus avisos), `HistoryScreen` (menú Exportar · Importar · Borrar todo), `ResultScreen` (campos, acción y botones), `LinkCard` (sitio real y avisos, `Amber`), `SettingsScreen` (igual que en las apps hermanas), `Parts` (`QrImage`, `BarImage`, iconos), `Chrome` y `SettingsParts`. Solo pintan y avisan. |
| `ui/theme/` | `Theme`: `ThemeMode` (Sistema, Claro, Oscuro, Negro de fábrica), `Accent` (azul de fábrica…) y `QrColors`. El QR se dibuja siempre negro sobre blanco. |
| `mcp/` | Modo MCP, controlado por Arka (`HubLink`: `link`, `connect`, `disconnect`, `status`, `tool`): `Tokens` (`qrc_…`, cifrados con `SecretBox`), `QrTools` (`qr_history`, `qr_item`, `qr_parse`, `qr_make`; también eventos; con escritura `qr_create`, `qr_delete`; a través de `QrData`; `ToolOutput` lleva la imagen PNG), `McpHandler`, `RelayLink` (relay de Stone), `McpMode` (`LiveData`) y `HubLink` (módulo `qr_`). |
| `ArtCard.kt` | Las imágenes de «Compartir un contacto»: el QR clásico y la tarjeta «Arbolito» (árbol 3D dibujado con `Canvas`), cada una leída de nuevo con ZXing (`verify`, entera y a la mitad) antes de mostrarse. `ContactReader.kt` lee solo el contacto elegido en el selector de Android. `ui/ContactShare.kt` es su estado y `ui/screens/ShareContactScreen.kt` + `ShareContactParts.kt` sus pantallas. |
| `Camera.kt` | `CameraView`: CameraX, trasera o frontal (vista previa y análisis del último fotograma en un hilo) → `Codes.readFrame`. |
| `Openers.kt` | Lo que hace cada botón: abrir enlace, marcar, correo, SMS, añadir contacto, mapa, Wi‑Fi (hoja «Guardar red» de Android 11+), copiar, compartir PNG (`FileProvider`, solo `cache/share`) y guardar en Imágenes › QR, «Añadir al calendario», leer el portapapeles y escribir o leer el CSV que el usuario elige. |
| `Summary.kt` | `SummaryLines` (pura, con prueba) y `SummaryProvider`: «Códigos recientes» para Inicio (`FAMILIA.md`), solo con la misma firma. |
| `MainActivity.kt` | Crea el estado, pide la cámara, lee imágenes (selector de fotos), recibe texto compartido (`ACTION_SEND`, solo llena «Crear»), exporta e importa con el selector de archivos, copia e «Ir al Hub». |
| `Feedback.kt` | Vibrar y pitido al leer (`ToneGenerator`, sin permisos). |

Reglas:
- Texto visible en `res/values/strings.xml` (español) y `values-en`.
- Colores siempre de `QrColors` (más `Amber` para avisos, en `LinkCard`).
- Archivos de pantalla de unas 150 líneas como mucho; si crece, se divide.
