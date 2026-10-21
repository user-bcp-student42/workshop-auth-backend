# Contrato de endpoints

## `POST /auth/login`

### 1. Qué hace

Autentica a una persona con su usuario y contraseña y, si las credenciales son válidas, devuelve un token y los datos básicos de su cuenta.

### 2. Autenticación

No requiere una sesión ni un encabezado `Authorization`; este endpoint es el que inicia la autenticación. Requiere `Content-Type: application/json` y produce `application/json`.

El acceso desde navegador está sujeto a CORS. En perfiles no productivos se permiten por defecto `http://localhost:4200` y `http://127.0.0.1:4200`. En producción la allowlist se configura con `AUTH_CORS_ORIGINS`.

### 3. Solicitud

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| `username` | string | sí | Usuario. Debe contener al menos un carácter distinto de espacio; el valor no se recorta antes de autenticar. |
| `password` | string | sí | Contraseña. Debe contener al menos un carácter distinto de espacio. |
| `client` | string | sí | Identificador del cliente; no puede ser nulo, vacío ni contener solo espacios. |

Ejemplo completo:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4. Respuesta exitosa

**200 OK**

| campo | tipo | descripcion |
|---|---|---|
| `token` | string | JWT compacto firmado con RS256, con claims `iss`, `sub`, `role`, `iat`, `exp` y `client`. `client` conserva el valor enviado en la solicitud. El valor del token cambia en cada emisión. |
| `expiresIn` | integer | Duración declarada en segundos: `3600`. |
| `user` | object | Datos básicos de la cuenta autenticada. |
| `user.username` | string | Nombre de usuario. |
| `user.displayName` | string | Nombre visible; el resource lo escapa para HTML. |
| `user.role` | string | Rol almacenado para la cuenta, por ejemplo `STUDENT` o `TRAINER` para las cuentas semilla. |
| `debug` | string | Indicador fijo del flujo actual: `authenticated-with-legacy-flow`. |

Ejemplo completo de respuesta (el token es ilustrativo y no es una credencial reutilizable):

```json
{
  "token": "<JWT RS256 con claims iss, sub, role, iat, exp y client>",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

### 5. Respuestas de error

**400 Bad Request**, cuando el cuerpo JSON es `null`, falta `username`, `password` o `client`, o uno de esos campos es nulo/vacío/solo espacios. El cuerpo contiene `message`:

```json
{"message":"Username is required"}
```

Para una solicitud nula, el mensaje es `Request is null`; para una contraseña ausente o vacía, es `Password is required`; para un `client` ausente o vacío, es `Client is required`.

**400 Bad Request**, si el cuerpo JSON no se puede deserializar. El estado es gestionado por Quarkus/Jackson, no por `AuthResource`; su cuerpo exacto no está fijado por el código ni por las pruebas.

Pregunta abierta: caracterizar el cuerpo de error para JSON malformado si un cliente necesita depender de su forma exacta.

**401 Unauthorized**, cuando no existe el usuario o la contraseña no coincide. El mensaje incluye el username recibido:

```json
{"message":"Invalid user student or password"}
```

**403 Forbidden**, cuando una solicitud de navegador incluye un `Origin` fuera de la allowlist CORS. Quarkus rechaza la solicitud antes de ejecutar el resource y la respuesta observada no tiene cuerpo:

```text
(cuerpo vacío)
```

**415 Unsupported Media Type**, si el `Content-Type` no es compatible con `application/json`, conforme a `@Consumes`. El cuerpo lo genera Quarkus y no está fijado por el resource.

Pregunta abierta: confirmar el cuerpo exacto de 415 si un consumidor necesita procesarlo.

**500 Internal Server Error**, si una excepción sale del servicio de autenticación y llega al `catch` del resource. El cliente recibe un mensaje genérico; el detalle se registra solo en el log del servidor:

```json
{"message":"Internal server error"}
```

Nota: los errores SQL que `LegacyDatabase` captura y convierte en resultado sin usuario terminan como `401`, no como `500`.

### 6. Notas de seguridad

- Las contraseñas se verifican contra hashes BCrypt; las contraseñas heredadas se migran durante la inicialización de la base.
- En producción no hay una clave de firma por defecto: `AUTH_TOKEN_PRIVATE_KEY` debe señalar a una clave privada RSA administrada fuera del repositorio; si falta o no se puede leer, no se pueden emitir tokens. `AUTH_TOKEN_ISSUER` configura el emisor. Nunca use la clave de pruebas en despliegues.
- El token se firma con RS256 y contiene `iss`, `sub`, `role`, `iat`, `exp` y `client`; `client` conserva el valor de la solicitud y `expiresIn` es `3600` segundos. El payload JWT está codificado, no cifrado, por lo que no debe contener datos sensibles.
- Este backend emite tokens, pero no valida ni revoca tokens entrantes. Los servicios consumidores deben verificar la firma con la clave pública correspondiente y validar issuer y expiración antes de confiar en sus claims.
- En producción configure `AUTH_CORS_ORIGINS` con orígenes explícitos y `AUTH_TOKEN_PRIVATE_KEY` con una clave administrada fuera del repositorio.

Fuentes de implementación: `src/main/java/com/bcp/workshop/auth/AuthResource.java` (validación, códigos y respuesta), `AuthenticationService.java` (verificación de credenciales), `LegacyDatabase.java` y `PasswordHasher.java` (persistencia y hash), `TokenIssuer.java` (JWT), `LoginRequest.java` y `AuthenticatedSession.java` (tipos), `src/main/resources/application.properties` (CORS, issuer y clave), y `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java` (respuestas verificadas).

## `GET /q/health`

### 1. Qué hace

Informa si el servicio está operativo para que un monitor u orquestador pueda decidir si debe seguir enviándole tráfico.

### 2. Autenticación

No requiere autenticación ni encabezado `Authorization`. Produce `application/json`. Las solicitudes desde navegador siguen sujetas a la misma allowlist CORS global indicada para `POST /auth/login`.

### 3. Solicitud

| campo | tipo | obligatorio | descripcion |
|---|---|---|---|
| cuerpo | ninguno | no | `GET /q/health` no define cuerpo ni parámetros de solicitud propios. |

No aplica un ejemplo JSON de solicitud: el endpoint no consume un cuerpo JSON.

### 4. Respuesta exitosa

**200 OK**, cuando el estado agregado de salud es `UP`.

| campo | tipo | descripcion |
|---|---|---|
| `status` | string | Estado agregado de los checks: `UP` cuando todos están operativos. |
| `checks` | array de object | Checks de salud incluidos en el estado agregado. Está vacío cuando no se registró ninguno. |

Ejemplo completo para este repositorio, que no declara checks propios:

```json
{
  "status": "UP",
  "checks": []
}
```

### 5. Respuestas de error

**503 Service Unavailable**, si un health check registrado informa `DOWN`. El proyecto no declara checks propios, por lo que no hay un caso `DOWN` concreto ni un nombre de check que pueda atribuirse a esta aplicación. SmallRye Health define el esquema `status`/`checks`; este ejemplo muestra la forma estándar condicional, no un resultado observado de este backend:

```json
{
  "status": "DOWN",
  "checks": [
    {
      "name": "nombre-del-check-que-fallo",
      "status": "DOWN"
    }
  ]
}
```

Pregunta abierta: si se agrega un check propio o una extensión registra checks, documentar su nombre, datos y condición exacta de fallo.

**500 Internal Server Error**, si el productor no puede procesar la solicitud de salud. La especificación MicroProfile Health define este estado sin cuerpo JSON; no hay una causa de este tipo definida por el código de la aplicación.

```text
(sin payload JSON)
```

**403 Forbidden**, para una solicitud de navegador con un `Origin` fuera de `AUTH_CORS_ORIGINS`; la política CORS global la rechaza antes del endpoint. El cuerpo observado por la prueba de login es vacío:

```text
(cuerpo vacío)
```

### 6. Notas de seguridad

- La ruta no autentica al consumidor; no debe incluir información sensible en los checks.
- La respuesta solo describe salud. No es una prueba de autenticación ni reemplaza la verificación de identidad.

Fuentes de implementación: `pom.xml` (extensión `quarkus-smallrye-health`), ausencia de clases `HealthCheck` en `src/main/java`, y `src/main/resources/application.properties` (host y CORS). Formato estándar: [guía SmallRye Health de Quarkus](https://quarkus.io/guides/smallrye-health) y [especificación MicroProfile Health 4.0](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html).

## Preguntas abiertas

- El cuerpo de errores de deserialización JSON y `415` depende de Quarkus/Jackson y no está fijado por pruebas del componente.
- El repositorio no registra health checks propios; si se agregan checks automáticos o de aplicación, confirmar el cuerpo `DOWN` exacto en ejecución.

## Documentos consultados

- `README.md`
- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/java/com/bcp/workshop/auth/AuthResource.java`
- `src/main/java/com/bcp/workshop/auth/AuthenticationService.java`
- `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`
- `src/main/java/com/bcp/workshop/auth/PasswordHasher.java`
- `src/main/java/com/bcp/workshop/auth/TokenIssuer.java`
- `src/main/java/com/bcp/workshop/auth/LoginRequest.java`
- `src/main/java/com/bcp/workshop/auth/AuthenticatedSession.java`
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`
- Guía oficial [SmallRye Health de Quarkus](https://quarkus.io/guides/smallrye-health)
- Especificación oficial [MicroProfile Health 4.0](https://download.eclipse.org/microprofile/microprofile-health-4.0/microprofile-health-spec-4.0.html)
- Guía oficial [SmallRye JWT Build de Quarkus](https://quarkus.io/guides/security-jwt-build)