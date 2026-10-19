# Plan de corrección
## Aprobador: Eric Llanos
## Fecha: 19/OCT/2026

## Selección

Se seleccionan los tres pendientes de mayor severidad: H-02 y H-03 (alta), y H-05 (media, vulnerabilidad). H-05 se prioriza sobre H-06, que también es media pero está clasificado como sugerencia. H-04 está resuelto y no forma parte de este plan.

El contrato de `POST /auth/login` se conserva en rutas, códigos HTTP y campos de éxito. Cualquier ajuste al cuerpo `500` para H-05 se documentará como cambio explícito de ese caso de error, sin alterar respuestas exitosas ni otros errores.

## Item: H-02 — Contraseñas almacenadas en texto plano

**Estado actual:** Pendiente, severidad alta, Vulnerabilidad, según `HALLAZGOS.md`.

**Problema:** la migración decide si una contraseña heredada ya está protegida usando solo una expresión regular sobre su forma BCrypt. Una contraseña en texto plano que coincida con ese patrón se omite, luego se interpreta como hash y no permite autenticar al usuario.

**Cambio propuesto:** sustituir la inferencia por patrón por metadatos explícitos de versión/formato de credencial, y hacer que el flujo de autenticación verifique contraseñas con BCrypt. Migrar las credenciales heredadas de forma transaccional e idempotente; no escribir contraseñas en claro ni registrarlas. Para filas antiguas sin procedencia fiable, una cadena con forma de BCrypt puede ser texto plano o un hash previo: no adivinar. Definir una transición explícita para esas filas ambiguas (por ejemplo, invalidación y restablecimiento de contraseña) y probarla antes del despliegue.

**Archivos a tocar:**

- `src/main/java/com/bcp/workshop/auth/PasswordHasher.java`: reemplazar la detección basada solo en patrón por operaciones coherentes con el formato/versionado persistido.
- `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`: añadir metadatos de formato o versión y migrar datos heredados con una política explícita para filas ambiguas.
- `src/main/java/com/bcp/workshop/auth/UserCredential.java`: transportar el formato/versión necesario para verificar sin inferencias.
- `src/main/java/com/bcp/workshop/auth/AuthenticationService.java`: verificar el hash según el formato persistido y rechazar de forma controlada credenciales que requieran restablecimiento.
- `src/test/java/com/bcp/workshop/auth/PasswordHasherTest.java` y `src/test/java/com/bcp/workshop/auth/LegacyDatabaseMigrationTest.java`: pruebas unitarias y de migración en SQLite temporal.
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: verificar que las credenciales válidas conservan el `200` y que una cuenta ambigua/bloqueada no inicia sesión, sin cambiar el contrato HTTP.

**Prueba que debe escribirse antes de tocar esos archivos:** crear una prueba de regresión con SQLite temporal donde una contraseña heredada sea literalmente un hash BCrypt válido (producido en el fixture), y demostrar que la migración no la deja pasar silenciosamente como hash ya establecido. Añadir caracterización de login de las cuentas semilla y de una fila que ya tenga metadatos de hash. La prueba debe fijar el comportamiento elegido para registros ambiguos (restablecimiento requerido) y verificar que reinicializar la base no rehashea ni altera hashes identificados explícitamente.

## Item: H-03 — Token reversible codificado en Base64

**Estado actual:** Pendiente, severidad alta, Vulnerabilidad, según `HALLAZGOS.md`.

**Problema:** el token codifica campos concatenados en Base64. El contenido puede leerse y alterarse; no existe firma que detecte modificaciones. La expiración se incluye como texto, pero no se valida en este backend.

**Cambio propuesto:** emitir un JWT firmado con una biblioteca mantenida compatible con Quarkus, claims explícitos (`sub`, rol y expiración `exp`) y una vida de 3600 segundos. Cargar la clave de firma desde configuración externa; no añadir claves privadas reales al repositorio. Mantener `token` como string y `expiresIn` como número con el mismo valor del contrato. La verificación por servicios consumidores queda fuera del repositorio; no afirmar que la emisión por sí sola autentica o valida solicitudes entrantes.

**Archivos a tocar:**

- `pom.xml`: añadir la extensión de construcción/verificación JWT de Quarkus que se elija, justificando que no existe una API adecuada en las dependencias actuales y manteniendo Dependency Review.
- `src/main/java/com/bcp/workshop/auth/TokenIssuer.java` y `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`: sustituir el token casero por emisión firmada y claims explícitos; conservar la interfaz de emisión necesaria para la respuesta.
- `src/main/resources/application.properties`: configurar la ubicación de la clave privada por ambiente sin incluir secretos reales.
- `src/test/java/com/bcp/workshop/auth/TokenIssuerTest.java`: verificar firma, claims y expiración con claves aisladas de prueba.
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: asegurar que la respuesta HTTP conserva `token`, `expiresIn`, `user` y `debug`.
- `docs/ENDPOINTS.md`: actualizar únicamente la nota de seguridad y descripción del formato del token; conservar ruta, status y campos del contrato.
- `src/test/resources/`: incluir solo material de firma de prueba, nunca una clave de producción.

**Prueba que debe escribirse antes de tocar esos archivos:** caracterizar primero el contrato HTTP de login exitoso (`200`, `token` string, `expiresIn=3600` y los mismos campos de usuario). Añadir una prueba unitaria para que un token emitido se verifique con una clave pública de prueba y contenga el sujeto, el rol y un `exp` aproximadamente 3600 segundos posterior a la emisión. Añadir una aserción de que la firma no valida con otra clave. No usar el formato Base64 anterior como requisito del cliente.

## Item: H-05 — Exposición de detalles internos en errores

**Estado actual:** Pendiente, severidad media, Vulnerabilidad, según `HALLAZGOS.md`.

**Problema:** el `catch` del resource devuelve `error.getMessage()` y `error.getClass().getName()` al cliente. El mensaje puede contener detalles de implementación o del entorno, y el nombre de clase facilita reconocimiento interno.

**Cambio propuesto:** conservar el status `500` y devolver un mensaje genérico estable sin incluir la clase ni el mensaje original. Registrar el detalle de la excepción únicamente en el log del servidor, sin contraseñas, tokens ni datos sensibles de la solicitud. Aplicar la responsabilidad de respuesta HTTP en el resource y logging controlado en una única ruta de error, evitando duplicar lógica. Actualizar la sección de errores de `docs/ENDPOINTS.md` solo para reflejar este cambio expresamente solicitado por H-05.

**Archivos a tocar:**

- `src/main/java/com/bcp/workshop/auth/AuthResource.java`: reemplazar la serialización de detalles internos por respuesta genérica y logging del lado servidor.
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`: caracterizar la forma actual y luego verificar la respuesta genérica sin `exception` ni mensaje interno.
- `docs/ENDPOINTS.md`: documentar la forma segura del cuerpo `500`; mantener intactos los demás casos del contrato.

**Prueba que debe escribirse antes de tocar esos archivos:** usar un `AuthenticationService` falso que arroje `IllegalStateException("database unavailable")` y capturar el comportamiento actual: status `500`, `message` con el detalle y campo `exception` con el nombre de clase. Registrar esa caracterización antes del cambio. La prueba de aceptación posterior deberá exigir status `500`, solo el mensaje genérico documentado, ausencia de `exception` y ausencia de `database unavailable` en el cuerpo; una prueba de captura de logs puede verificar que el error se conserva solo del lado servidor.

## Verificación común

Escribir y ejecutar cada prueba de caracterización de su propio item antes de modificar los archivos de producción de ese item. Implementar y revisar cada alcance por separado. Ejecutar `mvn clean verify`, conservar JaCoCo y alcanzar al menos 85% de cobertura del código modificado; no silenciar CodeQL ni Dependency Review. No usar secretos reales ni alterar rutas, cuerpos exitosos o status fuera del ajuste explícito del cuerpo `500` de H-05.