# QR (lector y creador de códigos MCP): reglas para agentes

- Lee `ARQUITECTURA.md` (y el contrato común `FAMILIA.md` del repo de Inicio) y abre solo los archivos que necesites.
- Pruebas: `cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (debe pasar todo).
  Nunca desactives ni saltes una prueba. Lint sin avisos nuevos.
- Cada versión: sube `versionCode` y `versionName`, copia el APK a `android/QR-<versión>.apk` (borra el anterior)
  y anótalo en `CHANGELOG.md`.
- Reglas puras (qué contiene un código, construirlo, avisos de enlaces, historial, leer y dibujar códigos, PNG) en `data/`,
  sin Android y con pruebas. Las pantallas solo pintan y avisan. Todo lo que abre otra app vive en `Openers.kt`.
- Leer códigos es en el teléfono (ZXing): sin internet ni servicios de Google. Los fotogramas de la cámara no se guardan.
- Seguridad: solo `http(s)` es enlace. Nada se abre solo ni por MCP: el usuario toca, y un enlace con avisos pide
  confirmar. Las contraseñas de Wi‑Fi no salen por MCP ni en el resumen de Inicio.
- Privacidad: historial y ajustes viven solo en el teléfono (fuera de copias). Nada sale salvo por el Modo MCP que el
  usuario active; internet es solo para eso.
- Cada herramienta MCP nueva lleva su prueba en `McpTest`: lista de herramientas, llamada correcta, argumentos malos y
  «no encontrado». Las que cambian algo exigen token de lectura y escritura (`READ_ONLY` si no).
- Prohibido: `eval`, motores de scripts, SQL concatenado.
- Firma: la de depuración del entorno, igual que el resto de la familia y Gateway Hub.
