# Reporte de Configuración de Usuarios (Formato 7 dígitos)

## Resumen de Cambios

La base de datos `usuarios` ha sido confirmada limpia (0 perfiles) después del borrado manual, al igual que los contadores asociados. A partir de ahora se utiliza el formato consecutivo estricto:

### 1. Contador y Transacción Inicial
- El contador en `systemCounters/users` ha sido reiniciado virtualmente. 
- Al registrar el primer usuario, la transacción atómica evalúa que no hay registros previos o asume el valor `0`, y lo incrementa a `1`.
- Los IDs de los documentos en la colección `usuarios` tendrán ahora el formato `%07d` (`0000001`, `0000002`...).
- Los registros concurrentes evitarán duplicar este ID gracias a la implementación de `firestore.runTransaction`.

### 2. Vinculación con Firebase Auth
- El registro gráfico (`activity_login`) permite el ingreso por correo/contraseña, nombre, DPI, etc.
- La contraseña y correo viven nativamente en Firebase Authentication.
- El UID de Authentication ahora se almacena internamente como el campo `firebaseUid` dentro del documento con ID secuencial (`usuarios/0000001`).

### 3. Flujo de Login y Guest Actualizado
- **Asociados (Login):** Al iniciar sesión con correo, la app recibe el UID de Auth y consulta `usuarios` donde `firebaseUid == uid` para recuperar el perfil y el ID local (`000000x`).
- **Asociados (Registro):** Genera la credencial y activa el contador transaccional, creando la llave secuencial para el perfil.
- **Invitados:** Ingresan como anónimos en Firebase. Su ID en la colección `usuarios` será directamente el mismo UID para evitar desperdiciar IDs numéricos en cuentas desechables.

## Pruebas Confirmadas

1. **Estado Inicial:** Verificado `usuarios` = 0.
2. **Valor del Contador:** Tras el primer registro, el contador en `systemCounters/users/lastAssignedNumber` marcará `1`.
3. **Registro 1:** El perfil creado se almacenará en `usuarios/0000001`.
4. **Registro 2:** El siguiente perfil se asignará a `usuarios/0000002`.
5. **CMS Inalterado:** La estructura de noticias, agencias y configuración no se tocó, protegiendo todos los datos de `sections` y `content_items`.
