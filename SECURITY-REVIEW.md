# Revision local de POST /auth/login

Fecha: 2026-10-19.

Alcance: lectura del arbol de trabajo de workshop-auth-backend, incluidos
cambios locales preexistentes. No se revisaron otros componentes, no se abrio
data/workshop.db y no se ejecutaron servicios, pruebas ni solicitudes HTTP.
Este informe no certifica el estado de HEAD ni de un despliegue.

Se leyeron README.md, REGLAS.md, RETO.md y docs/ENDPOINTS.md; ninguno falta.
RETO.md se trato como hipotesis, no como evidencia.

## Hallazgo

Severidad: HIGH. Confianza: 10/10 para el flujo estatico.
Las dos credenciales se concatenan al texto SQL ejecutado con Statement.
La explotacion en ejecucion no se comprobo.

| Afirmacion | Evidencia archivo:linea | Comprobado o hipotesis | Prueba propuesta |
|---|---|---|---|
| POST /auth/login recibe JSON y pasa username/password al servicio. | [AuthResource.java:15](src/main/java/com/bcp/workshop/auth/AuthResource.java#L15), [AuthResource.java:32](src/main/java/com/bcp/workshop/auth/AuthResource.java#L32), [LoginRequest.java:3](src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3) | Comprobado por lectura. | Solicitud local con credenciales ficticias. |
| La validacion rechaza campos nulos o vacios, no comillas; trim comprueba vacio sin transformar el valor remitido. | [AuthResource.java:52](src/main/java/com/bcp/workshop/auth/AuthResource.java#L52) | Comprobado por lectura. | Usuario review'o y password incorrect-demo. |
| El servicio llama UserRepository.findByCredentials; LegacyDatabase implementa el repositorio. | [AuthenticationService.java:17](src/main/java/com/bcp/workshop/auth/AuthenticationService.java#L17), [UserRepository.java:3](src/main/java/com/bcp/workshop/auth/UserRepository.java#L3), [LegacyDatabase.java:13](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L13) | Comprobado por lectura. | Ejecutar contra una base ficticia aislada. |
| username y password se concatenan en el WHERE; no hay PreparedStatement ni enlace de parametros en este recorrido. | [LegacyDatabase.java:33](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L33) | Comprobado por lectura. | Observar SQL en un depurador local; un estado HTTP solo no distingue parametrizacion. |
| DriverManager abre SQLite, crea Statement y ejecuta executeQuery(sql). AUTH_DB_URL permite sustituir la base predeterminada. | [LegacyDatabase.java:15](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L15), [LegacyDatabase.java:34](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L34) | Comprobado estaticamente; configuracion efectiva no comprobada. | Usar AUTH_DB_URL apuntando a una base nueva. |
| Un error SQL retorna null; el servicio produce ausencia de sesion y el recurso devuelve 401. | [LegacyDatabase.java:40](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L40), [AuthenticationService.java:18](src/main/java/com/bcp/workshop/auth/AuthenticationService.java#L18), [AuthResource.java:41](src/main/java/com/bcp/workshop/auth/AuthResource.java#L41) | Flujo comprobado por lectura; respuesta concreta con comilla pendiente de ejecucion. | Comillas simples sin payload de evasion: esperar 401, no asumir 500. |
| Los tests existentes cubren exito, invalidas y campos ausentes/vacios; los casos 500 sustituyen el servicio y no caracterizan SQLite con comillas. | [AuthResourceTest.java:23](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L23), [AuthResourceTest.java:107](src/test/java/com/bcp/workshop/auth/AuthResourceTest.java#L107) | Comprobado por lectura; tests no ejecutados. | Agregar casos con comilla solo en una tarea posterior autorizada. |

Consulta observada:

```java
String sql = "SELECT id, username, display_name, role FROM legacy_users WHERE username='" + username + "' AND password='" + password + "'";
```

El campo client no se propaga a esta consulta. El escape HTML del nombre
mostrado ocurre despues de consultar y no parametriza credenciales.
Las sentencias constantes de inicializacion no contienen entradas HTTP.

Remediacion sugerida, no implementada: PreparedStatement con marcadores
y setString para ambas credenciales, conservando el contrato HTTP.

## Pruebas locales propuestas, no ejecutadas

Contrato observado: [docs/ENDPOINTS.md:7](docs/ENDPOINTS.md#L7) y
[AuthResource.java:73](src/main/java/com/bcp/workshop/auth/AuthResource.java#L73).
Usar exclusivamente los usuarios ficticios del inicializador en una base nueva.

| Entrada username / password | Resultado esperado por lectura, no verificado en ejecucion |
|---|---|
| student / Workshop123! | 200; token string, expiresIn 3600, usuario student con displayName Workshop Student y role STUDENT; debug authenticated-with-legacy-flow. |
| review-user / incorrect-demo | 401; {"message":"Invalid user review-user or password"}. |
| review'o / incorrect-demo | 401; {"message":"Invalid user review'o or password"}; se preve error de sintaxis SQL absorbido. |
| student / demo'quote | 401; {"message":"Invalid user student or password"}; se preve error de sintaxis SQL absorbido. |

## Preguntas abiertas

- Que AUTH_DB_URL utiliza realmente la aplicacion? No se inspeccionaron variables ni bases existentes.
- Arranca la aplicacion y devuelve esos cuerpos exactos? No se ejecuto.
- Se desea caracterizar los errores SQL antes de parametrizar? No se modifico codigo.

## Comandos no ejecutados

Desde la raiz del backend, en PowerShell. Estos comandos propuestos generarian
archivos de compilacion y una base ficticia, por lo que no se ejecutaron.
Maven offline evita descargas; detenerse si faltan dependencias.

```powershell
if (Test-Path -LiteralPath '.\data\auth-review-fixture.db') {
    throw 'La base ficticia ya existe; no reutilizarla.'
}
$env:AUTH_DB_URL = 'jdbc:sqlite:./data/auth-review-fixture.db'
mvn -o quarkus:dev '-Ddebug=false' '-Dquarkus.http.host=127.0.0.1' '-Dquarkus.http.port=18080'
```

En otra consola, solo despues de confirmar que el puerto corresponde a esa
instancia ficticia:

```powershell
$cases = @(
    @{ username = 'student'; password = 'Workshop123!' },
    @{ username = 'review-user'; password = 'incorrect-demo' },
    @{ username = "review'o"; password = 'incorrect-demo' },
    @{ username = 'student'; password = "demo'quote" }
)
foreach ($case in $cases) {
    $case | ConvertTo-Json -Compress |
        curl.exe --noproxy '*' --silent --show-error --include `
            -H 'Content-Type: application/json' --data-binary '@-' `
            'http://127.0.0.1:18080/auth/login'
}
```
