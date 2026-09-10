# COLUA Noticias

## Identidad

- **Nombre actual de la app:** COLUA Noticias.
- **Organización:** COLUA R.L., cooperativa de ahorro y crédito.
- **Propósito:** Digitalizar la información que antes se difundía mediante trifoliares, boletines y material impreso, facilitando la difusión institucional y orientativa.
- **Nota:** El nombre comercial de la aplicación podría cambiar en el futuro.

## Descripción de la App

**COLUA Noticias** es una plataforma digital informativa de COLUA R.L. Su objetivo principal es facilitar a asociados, invitados y personas interesadas el acceso a información clara, transparente y actualizada sobre los productos, servicios, beneficios, agencias, noticias y programas de la cooperativa.

> **Aviso Importante:** La aplicación tiene un carácter puramente **informativo y de orientación**. No constituye ni opera como una plataforma de banca móvil; no permite realizar transacciones monetarias, pagos, transferencias, consulta de saldos en tiempo real ni apertura de cuentas financieras.

## Contenido y Secciones

La aplicación incluye las siguientes secciones operativas y de consulta implementadas:

- **Cuentas de ahorro:** Información detallada sobre líneas de ahorro (infanto-juvenil, programado, etc.).
- **Créditos:** Conozca las opciones de financiamiento disponibles (productivo, consumo, vivienda, vehículo).
- **Seguros:** Opciones de protección para asociados y sus familias (vida, salud, etc.).
- **Remesas:** Orientación y recepción de remesas familiares e internacionales (ej. Western Union).
- **Servicios digitales:** Canales de atención digital e informativa de la cooperativa.
- **Beneficios para asociados:** Ventajas exclusivas y valor de pertenecer a la cooperativa.
- **Agencias:** Módulo completo de búsqueda, filtros por departamento y tipo de atención (agencia, agente, cajero), ubicación, números de contacto y horarios.
- **Noticias y comunicados:** Actualidad institucional y avisos relevantes de COLUA R.L.
- **Información institucional:** Propuesta de valor, visión, principios, valores y PBX de contacto.
- **Sostenibilidad cooperativa:** Programas de educación, empleabilidad, desarrollo comunitario y proyectos sustentables.
- **Panel administrativo:** Herramienta interna para gestionar dinámicamente el contenido, secciones, bloques informativos, agencias y publicar/sincronizar actualizaciones.

## Acceso de Usuarios

La aplicación soporta dos modalidades diferenciadas de acceso:

| Tipo | Datos solicitados | Uso principal |
|---|---|---|
| **Asociado** | Nombre completo, DPI y teléfono | Acceso e identificación del asociado (ID oficial con formato `user0000000`). |
| **Invitado** | No solicita datos personales | Consulta libre del contenido público y divulgativo (ID temporal `guest_{uuid}`). |

### Reglas de Gestión de Datos de Usuario:
- **Normalización de DPI:** Se eliminan automáticamente espacios y caracteres no numéricos al procesar el DPI.
- **Identificadores:** Está estrictamente prohibido usar el DPI, número de teléfono, fecha o modelo del dispositivo como ID de documento.
- **Aislamiento:** No se deben mezclar datos de usuarios invitados con registros de asociados.
- **Privacidad y Datos Personales:** El nombre, DPI y teléfono se tratan con estricta confidencialidad. Queda prohibido exponer información personal en registros de logs (`Logcat`), ejemplos de código o mensajes de error.

## Estructura de Usuarios y Dispositivos

El sistema y el modelo de datos distinguen claramente entre un usuario y un dispositivo instalado, estructurándose bajo la siguiente jerarquía conceptual:

```text
usuarios/{userId}
usuarios/{userId}/dispositivos/{installationId}
```

## Stack Tecnológico

- **Plataforma:** Android (SDK min 24, target 35/36).
- **Lenguajes:** Java y Kotlin.
- **Interfaz de usuario:** XML Views, Material Design 3 y Jetpack Compose (componentes híbridos e interoperables).
- **Persistecia local:** Room Database con caché offline y `DataSeeder` inicial.
- **Sincronización y Backend:** Firebase (Firestore, Auth, Analytics).
