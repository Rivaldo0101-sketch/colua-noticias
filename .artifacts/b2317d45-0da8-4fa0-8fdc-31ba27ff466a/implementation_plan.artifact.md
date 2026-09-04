# Plan de Corrección Integral de Usuarios, Migración y Estabilidad

Este plan aborda de raíz los problemas de duplicados de usuarios, modelo de datos en Firestore, persistencia tras desinstalación y estabilidad (reinicios/pantalla blanca).

## User Review Required

> [!IMPORTANT]
> - Se cambiará el formato del Document ID en Firestore para seguir estrictamente el patrón `user0000000`, `user0000001`, etc. (7 dígitos, minúsculas).
> - Se implementará una subcolección de dispositivos `usuarios/{userId}/dispositivos/{installationId}` para separar persona de dispositivo.
> - Se utilizará una transacción atómica con contador central (`systemCounters/users`) para garantizar IDs secuenciales sin duplicados concurrentes.
> - Se implementará un script de migración automática que agrupa documentos legacy por DPI normalizado, crea el documento único `userXXXXXXX`, migra los dispositivos a la subcolección y borra los documentos antiguos.

## Proposed Changes

### Repositorio y Lógica de Firestore (`ColuaRepository.kt`)
- **Contador Atómico**: Implementar transacción Firestore en `systemCounters/users` para obtener el siguiente ID `userXXXXXXX`.
- **Normalización de DPI**: Eliminar espacios, guiones y caracteres no alfanuméricos (`dpi.replaceAll("[^0-9a-zA-Z]", "").toLowerCase(Locale.getDefault())`).
- **Registro con Deduplicación por DPI**:
  1. Normalizar DPI.
  2. Buscar en Firestore si ya existe un usuario con ese `dpiNormalizado`.
  3. Si existe: reutilizar su `userId` (`user000000X`), actualizar datos del usuario y registrar/actualizar el dispositivo actual en `usuarios/{userId}/dispositivos/{installationId}`.
  4. Si no existe: obtener nuevo ID mediante transacción, crear documento `usuarios/{userId}` y su dispositivo en la subcolección.
- **Subcolección de Dispositivos**: Guardar metadatos de cada dispositivo (`installationId`, `modeloDispositivo`, `fabricante`, `primeraActividad`, `ultimaActividad`, `activo`) en `usuarios/{userId}/dispositivos/{installationId}`.
- **Migración de Datos Legacy**: Añadir rutina de migración que convierta los documentos antiguos (`{dpi}_{model}`, `INVITADO_{installId}`, etc.) al nuevo esquema limpio `userXXXXXXX` con subcolección de dispositivos y limpie la colección.

### Autenticación y Registro (`LoginActivity.java` y `MainActivity.java`)
- **Flujo de Registro Inicial**: Asegurar que en instalación limpia (donde `UserPrefs` no tiene sesión guardada), se muestre obligatoriamente `LoginActivity`.
- **Prevención de Bucles y Reinicios**: Eliminar manejadores de excepciones globales que provocan reinicios infinitos en bucle. Reemplazar con manejo de errores controlado y estados de carga con timeout.

## Verification Plan

### Automated Tests
- Compilación del proyecto (`gradle_build` app:assembleDebug).
- Verificación de ejecución sin errores.

### Manual Verification
1. Registro de usuario nuevo (`user0000000`).
2. Registro de segundo usuario (`user0000001`).
3. Registro con el mismo DPI desde otro dispositivo/instalación (reutiliza `userId` y añade dispositivo a la subcolección).
4. Verificación en Firestore de que solo existen IDs `user` + 7 dígitos y subcolección `dispositivos`.
5. Comprobación de apertura y cierre repetido sin reinicios ni pantalla blanca.
