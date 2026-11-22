# QR (lector y creador de códigos MCP)

Lector y creador de códigos QR y de barras para Android, en negro con acento azul. Parte de la familia de Stone, Luma,
Calculadora, Notas, Filem, Brújula, Nivel, Reloj, Calendario e Inicio: tu IA (Claude, ChatGPT…) puede **leer tu
historial y crear códigos** por MCP.

## Estado (0.1.0)

- **Escanear:** la cámara a pantalla completa con un marco; lee QR, Data Matrix, Aztec, PDF417 y, si quieres, códigos de
  barras de productos y libros (EAN, UPC, Code 128…), también de lado. Linterna y **Desde imagen** (selector de fotos de
  Android, sin permiso). Todo se lee **en el teléfono, sin internet ni servicios de Google** (ZXing).
- **Resultado:** qué contiene (Wi‑Fi, contacto, enlace, teléfono, correo, SMS, lugar o texto) y el botón que toca:
  **Conectar a la red** (Android pregunta antes de guardarla), **Añadir contacto**, **Llamar**, **Escribir correo/SMS**,
  **Ver en el mapa** o **Abrir enlace**. Copiar, Compartir (imagen PNG), Guardar en Imágenes › QR, Favorito, Nombre,
  Editar y Eliminar. La contraseña del Wi‑Fi se ve solo al tocarla.
- **Enlaces seguros:** antes de abrir, el **sitio real** en grande y avisos: sin candado (http), dirección numérica, letras
  de otro alfabeto (xn--), acortadores, «usuario@» antes del sitio, puertos raros y descargas de apps (.apk). Con avisos,
  pide confirmar y lo destacado es «No abrir». Solo `http(s)` cuenta como enlace (nunca `javascript:` ni `intent:`).
- **Crear:** Texto, Enlace, Wi‑Fi (WPA/WEP/abierta, oculta), Contacto, Teléfono, Correo y SMS, con el código dibujado al
  escribir y «Guardar y compartir».
- **Historial:** buscar sin acentos; Todos, Leídos, Creados y Favoritos. Hasta 500 (los favoritos no se borran solos).
- **Ajustes:** códigos de barras, vibrar, guardar historial, borrar historial, **Modo MCP**, Temas, privacidad y versión.
- **Modo MCP:** `qr_history`, `qr_item`, `qr_parse`, `qr_make` (devuelve la imagen PNG) y, con token de lectura y
  escritura, `qr_create` y `qr_delete`. Tokens `qrc_…`; módulo `qr_` para Gateway Hub. La IA **no** usa la cámara ni
  abre enlaces, y nunca ve contraseñas de Wi‑Fi.
- **Resumen para Inicio:** «Códigos recientes» (los de esta semana) en la pantalla «Hoy» de Inicio.
- Sin anuncios ni estadísticas; internet solo para el Modo MCP (apagado de fábrica).

APK: [`android/QR-0.1.0.apk`](android/QR-0.1.0.apk) (`com.zirakusa.qr`, firmado como el resto de la familia y Gateway Hub).

## Compilar y probar

```bash
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

## Siguiente

Ver [`ROADMAP.md`](ROADMAP.md).
