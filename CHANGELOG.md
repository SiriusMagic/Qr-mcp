# QR 0.3.0: compartir contactos con un arbolito

- **Compartir un contacto** (Crear › «Compartir un contacto»): eliges un contacto con el selector de Android, marcas qué
  datos van (nombre, teléfonos, correos, empresa, cargo, dirección, web, nota) y ves una **vista previa** de exactamente lo
  que verá quien lo escanee. El código es una **vCard estándar**: cualquier cámara de teléfono la lee y ofrece guardarla
  en la agenda.
- **Diseño «Arbolito»**: un arbolito en 3D dibujado en el teléfono (diseño propio) acompaña al QR, que queda cuadrado,
  negro sobre blanco, con su margen y sin nada encima. Con «Clásico» vuelves al QR de siempre. **Guardar imagen** y
  **Compartir** (que pregunta antes y dice qué datos van).
- **Comprobado antes de mostrarlo**: cada imagen se vuelve a leer en el teléfono (entera y a la mitad de tamaño) y debe dar
  exactamente la vCard. Si el arbolito no se lee bien, se muestra el QR clásico y te lo dice.
- **Al escanear un contacto** se ven todos sus datos (también los teléfonos y correos extra, dirección, web y cargo) y
  «Añadir contacto» **pide confirmar** antes de abrir el formulario de contacto nuevo, donde tú decides si lo guardas.
- **Privacidad**: el contacto se lee solo en el teléfono, solo el que eliges; no se sube a ningún servidor, no va al
  historial y la IA (Modo MCP) no lo ve. El permiso de contactos solo se pide si Android lo exige para leer el elegido.
- **Iconos nuevos**: Luna (el de fábrica), Pino, Lila, Sauce, Otoño y **Cerezo** (seis). Se quitan los anteriores; si
  tenías uno elegido, pasa solo al nuevo que ocupa su lugar.
- 29 pruebas.

# QR 0.2.0

- **Ajustes de lectura nuevos**: «Reproducir sonido» (pitido), «Copiar al portapapeles» al leer, «Escaneo continuo» (sigue
  leyendo y guarda cada código en el historial con una nota breve, sin abrirlo), «Confirmar escaneos manualmente» (muestra
  el código y espera «Usar código») y «Cámara» trasera o frontal (también con el botón «Cambiar cámara»). Todos apagados
  de fábrica, salvo la cámara trasera.
- **Guardar duplicados** en Ajustes › Historial: apagado (de fábrica), un código repetido sube arriba en vez de repetirse.
- **Crear evento**: título, inicio, fin y lugar → QR de calendario (VEVENT). Al leer uno, «Añadir al calendario» abre tu
  calendario con los datos para que lo guardes tú.
- **Crear códigos de barras**: EAN‑13, EAN‑8, UPC‑A (el dígito de control se calcula o se comprueba), Code 128 y Code 39;
  se pueden compartir y guardar como imagen.
- **Desde el portapapeles** en «Crear», y **compartir texto** desde otra app hacia QR: llena «Crear», no abre nada.
- **Historial › menú**: «Exportar (CSV)» e «Importar (CSV)» con el selector de archivos de Android, y «Borrar todo» (pide
  confirmar; también borra favoritos).
- La barra de abajo tiene ahora **Ajustes**.
- Modo MCP: `qr_make` y `qr_create` hacen también eventos (`title`, `start`, `end`, `location`).
- Sin anuncios y sin internet nuevo; los enlaces siguen sin abrirse solos.
- 24 pruebas.

# QR 0.1.1

- **Conectar todas** (Gateway Hub 0.0.21): con un toque en el Hub se activa aquí «Redireccionar a Hub» y la app queda
  vinculada, sin entrar en ella. Solo lo puede pedir una app firmada por el mismo desarrollador.
- **Lucía** (la voz del Hub) usa las herramientas de esta app en el teléfono, **sin internet**, con las mismas reglas que
  por MCP. Solo mientras «Redireccionar a Hub» esté activado.
- **Icono de la app**: 5 iconos para elegir (Original, Claro, Dorado, Azul y Verde) en Ajustes › Icono de la app. Tras esta
  actualización quizá tengas que volver a poner el icono en tu pantalla de inicio (una sola vez).

# QR 0.1.0

Primera versión: lector y creador de códigos en negro con acento azul.
- Escanear con la cámara (QR, Data Matrix, Aztec, PDF417 y códigos de barras, también de lado), linterna y desde imagen;
  todo en el teléfono con ZXing, sin internet ni servicios de Google.
- Resultado con el botón que toca (Wi‑Fi, contacto, llamar, correo, SMS, mapa, enlace), copiar, compartir PNG, guardar en
  Imágenes › QR, favorito, nombre, editar y eliminar.
- Enlaces: sitio real y avisos (http, IP, xn--, acortadores, «usuario@», puertos, .apk); con avisos pide confirmar.
- Crear: texto, enlace, Wi‑Fi, contacto, teléfono, correo y SMS, con el código en vivo.
- Historial con búsqueda y filtros; ajustes de lectura e historial; Temas.
- Modo MCP (`qrc_`, módulo `qr_`): `qr_history`, `qr_item`, `qr_parse`, `qr_make` (con imagen), `qr_create`, `qr_delete`.
- Resumen «Códigos recientes» para la pantalla «Hoy» de Inicio.
- 14 pruebas.
