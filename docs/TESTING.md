# Plan y resultados de pruebas

Alcance: endpoints descritos en `docs/ENDPOINTS.md`. Las pruebas HTTP usan QuarkusTest y datos de laboratorio; el test de health `DOWN` registra un check solo en el perfil de prueba `health-down`. No se alteró código de producción.

## Matriz

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| `POST /auth/login` | Login con `student` y `Workshop123!` | Camino feliz | `200`; token no nulo, `expiresIn=3600`, usuario y campos documentados. `logsInWithWorkshopUser`. |
| `POST /auth/login` | Login con `trainer`, omitiendo `client` | Caso borde | `200`; campos de respuesta preservados. `logsInWithTrainerWithoutChangingResponseContract`. |
| `POST /auth/login` | `username` con espacio inicial | Caso borde | `401`; no se recorta el valor antes de autenticar. `rejectsUsernameWithLeadingWhitespaceInsteadOfTrimmingIt`. |
| `POST /auth/login` | `client` presente | Camino feliz | `200`; campo aceptado e ignorado. Cubierto por `logsInWithWorkshopUser`. |
| `POST /auth/login` | Solicitud JSON `null` | Error | `400`, `{"message":"Request is null"}`. `rejectsNullRequest`. |
| `POST /auth/login` | `username` ausente, nulo o solo espacios | Error | `400`, `{"message":"Username is required"}`. `rejectsMissingUsername`, `rejectsBlankUsername`. |
| `POST /auth/login` | `password` ausente, nulo o solo espacios | Error | `400`, `{"message":"Password is required"}`. `rejectsMissingPassword`, `rejectsBlankPassword`. |
| `POST /auth/login` | JSON malformado | Error | `400`; cuerpo generado por Quarkus/Jackson no asertado porque no está fijado por el resource. `rejectsMalformedJson`. |
| `POST /auth/login` | Usuario inexistente o contraseña incorrecta | Error | `401` y mensaje `Invalid user <username> or password`. `rejectsInvalidCredentialsWithUsernameInMessage`, `rejectsIncorrectPasswordForKnownUser`. |
| `POST /auth/login` | Origen CORS local permitido | Camino feliz | `200`, `Access-Control-Allow-Origin` coincide con el origen y respuesta JSON intacta. `allowsLocalFrontendOriginWithoutChangingLoginResponse`. |
| `POST /auth/login` | Origen CORS no permitido | Error | `403`, sin `Access-Control-Allow-Origin`. `doesNotAllowUnconfiguredFrontendOrigin`. |
| `POST /auth/login` | `Content-Type: text/plain` | Error | `415`; cuerpo de framework no asertado. `rejectsUnsupportedContentType`. |
| `POST /auth/login` | Excepción del servicio con mensaje y sin mensaje | Error | `500`; cuerpo contiene `message` y `exception`, o `Unknown error` si falta mensaje. `returnsInternalServerErrorWhenAuthenticationFails`, `returnsFallbackMessageWhenAuthenticationErrorHasNoMessage` (prueba directa del resource). |
| `GET /q/health` | Sin checks registrados | Camino feliz / caso borde | `200`, `{"status":"UP","checks":[]}`. `reportsUpWhenNoHealthChecksAreRegistered`. |
| `GET /q/health` | Origen CORS no permitido | Error | `403`, sin `Access-Control-Allow-Origin`. `rejectsUnconfiguredBrowserOrigin`. |
| `GET /q/health` | Check registrado informa `DOWN` | Error | `503`, `status=DOWN` y check `test-down-check` en el cuerpo. `reportsServiceUnavailableWhenARegisteredCheckIsDown`; usa perfil y check solo de prueba. |
| `GET /q/health` | Error del productor al procesar el health check | Pendiente | La especificación documenta `500` sin payload, pero el backend no ofrece un mecanismo para provocar de forma determinista ese fallo. No existe test ejecutado. |

## Código de las pruebas añadidas

Los métodos se encuentran en `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`, `HealthEndpointTest.java` y `HealthDownEndpointTest.java`. Las pruebas previas de validación, persistencia, hash y token siguen en sus respectivos archivos de test.

### Login: borde y respuestas de framework

```java
@Test
void rejectsUsernameWithLeadingWhitespaceInsteadOfTrimmingIt() {
    given()
            .contentType(JSON_CONTENT_TYPE)
            .body("""
                    {"username":" student","password":"Workshop123!"}
                    """)
            .when().post(LOGIN_PATH)
            .then()
            .statusCode(401);
}

@Test
void rejectsMalformedJson() {
    given()
            .contentType(JSON_CONTENT_TYPE)
            .body("{broken-json")
            .when().post(LOGIN_PATH)
            .then()
            .statusCode(400);
}

@Test
void rejectsUnsupportedContentType() {
    given()
            .contentType("text/plain")
            .body("username=student&password=Workshop123!")
            .when().post(LOGIN_PATH)
            .then()
            .statusCode(415);
}
```

### Health: UP, CORS y DOWN

```java
@QuarkusTest
class HealthEndpointTest {
    private static final String HEALTH_PATH = "/q/health";

    @Test
    void reportsUpWhenNoHealthChecksAreRegistered() {
        given()
                .when().get(HEALTH_PATH)
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void rejectsUnconfiguredBrowserOrigin() {
        given()
                .header("Origin", "https://untrusted.example")
                .when().get(HEALTH_PATH)
                .then()
                .statusCode(403)
                .header("Access-Control-Allow-Origin", nullValue());
    }
}

@QuarkusTest
@TestProfile(HealthDownTestProfile.class)
class HealthDownEndpointTest {
    @Test
    void reportsServiceUnavailableWhenARegisteredCheckIsDown() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(503)
                .body("status", is("DOWN"))
                .body("checks[0].name", is("test-down-check"))
                .body("checks[0].status", is("DOWN"));
    }
}
```

El check sintético se activa solo en el perfil `health-down`; no añade checks al runtime normal:

```java
@Readiness
@ApplicationScoped
@IfBuildProfile("health-down")
public class TestOnlyFailingHealthCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.down("test-down-check");
    }
}
```

## Comandos y medición

Los comandos de la matriz se ejecutaron antes de la actualización con Temurin JDK `21.0.12.1`; el puerto HTTP de test se fijó a `18081` porque el puerto por defecto estaba ocupado. La verificación posterior a la actualización se ejecutó desde la raíz del componente en PowerShell con Microsoft JDK `25.0.4.1` y Maven `3.10.0`.

| comando | directorio | código de salida | resultado observado |
|---|---|---:|---|
| `mvn -B verify` | raíz de `workshop-auth-backend/` | `0` | BUILD SUCCESS; 26 tests, 0 fallos, 0 errores. |
| `mvn "-Dquarkus.http.test-port=18081" "-Dtest=AuthResourceTest,HealthEndpointTest,HealthDownEndpointTest" test` | raíz de `workshop-auth-backend/` | `0` | BUILD SUCCESS; 19 tests, 0 fallos, 0 errores. |
| `mvn "-Dquarkus.http.test-port=18081" clean verify` | raíz de `workshop-auth-backend/` | `0` | BUILD SUCCESS; 22 tests, 0 fallos, 0 errores; reporte JaCoCo generado. |

**Medición JaCoCo:** plugin `org.jacoco:jacoco-maven-plugin:0.8.15`, ejecución `report` en la fase `verify`. Reporte: `target/site/jacoco/index.html`; fuente numérica: `target/site/jacoco/jacoco.xml`. Cobertura de líneas de producción: `166/178 = 93.3%`. `LegacyDatabase` tiene `89.9%` (80/89 líneas); las demás clases productivas del bundle tienen `100%`. El umbral de REGLAS.md es `85%`; no se deshabilitó ni redujo ningún quality gate.

## Fallos

No hubo fallos en las ejecuciones documentadas: ambas terminaron con código `0`.

## Pendientes

- `GET /q/health` con `500`: no se ejecutó una prueba porque la app no tiene un fallo de productor inducible y no se debe fabricar un resultado ajeno al comportamiento actual.
- Los cuerpos de error de `POST /auth/login` para JSON malformado y `415` no se comparan byte a byte; las pruebas verifican los estados `400` y `415` documentados, y `docs/ENDPOINTS.md` deja sus cuerpos como dependientes de Quarkus/Jackson.

## Pruebas propuestas

- Añadir una forma soportada de inyectar un fallo del productor de SmallRye Health en test y comprobar el `500` documentado sin cambiar el comportamiento productivo.
- Si los clientes necesitan depender del cuerpo de `400` por JSON malformado o `415`, capturar y fijar esos cuerpos mediante una prueba de caracterización de la versión actual de Quarkus antes de documentarlos como contrato estable.

## Documentos consultados

- `docs/ENDPOINTS.md`
- `REGLAS.md`
- `README.md`
- `docs/GOOD_PRACTICES.md`
- `pom.xml`
- `src/main/resources/application.properties`
- Código de `AuthResource`, `AuthenticationService`, `LegacyDatabase`, `PasswordHasher` y SmallRye Health.
