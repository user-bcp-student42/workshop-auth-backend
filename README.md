# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 25 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: SQL concatenado, contraseñas en texto plano, token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs.

## Dependency Review en GitHub Actions

El workflow requiere Dependency graph habilitado y, para repositorios privados, GitHub Code Security.
Si la API indica explícitamente que la funcionalidad no está disponible, el análisis se omite con una
advertencia y un resumen en Actions; esto no significa que las dependencias estén libres de vulnerabilidades.
Habilitar la funcionalidad en **Settings → Code security** permite ejecutar el análisis y bloquear
dependencias nuevas con vulnerabilidades de severidad alta o crítica. Los demás errores de la API siguen
provocando un fallo del job.
