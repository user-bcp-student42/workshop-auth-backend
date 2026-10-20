---
name: rv
description: 'Revisa pull requests con checklist de contratos, seguridad, calidad, pruebas, documentación y versionamiento. Usar al revisar un PR, evaluar cambios propuestos o preparar una revisión antes del merge.'
---

# RV — Revisión de Pull Requests

## Objetivo

Evaluar un pull request con evidencia del diff y del repositorio, identificando regresiones y dejando visibles los criterios sin evidencia. Este skill es de revisión: no modifica archivos, no aprueba ni fusiona el PR.

## Procedimiento

1. Identifica el repositorio, PR, rama base y alcance declarado. Lee la plantilla de PR y las instrucciones relevantes del repositorio.
2. Inspecciona el diff completo y suficiente contexto de los archivos afectados. Distingue cambios del PR de modificaciones preexistentes.
3. Evalúa cada categoría del checklist. Para cada observación, cita archivo y línea del diff o referencia verificable a un check; no infieras éxito por ausencia de errores visibles.
4. Prioriza defectos concretos introducidos por el PR. No reportes preferencias de estilo ni problemas preexistentes salvo que el cambio los agrave.
5. Resume hallazgos primero, ordenados por severidad. Si no hay hallazgos, dilo y menciona los límites de la revisión.
6. Detente sin modificar, aprobar ni fusionar el PR.

## Checklist

### Contratos
- ¿Se preservan rutas, métodos, esquemas, códigos HTTP, eventos, formatos persistidos y comportamiento observable?
- ¿Los cambios incompatibles están declarados, justificados y acompañados por estrategia de migración o transición cuando corresponda?
- ¿Los consumidores, productores y contratos protegidos afectados están identificados y cubiertos por pruebas?

### Seguridad
- ¿La autorización y validación ocurren en el servidor donde corresponde?
- ¿Entradas, consultas, archivos, errores, logs y datos sensibles se manejan de forma segura?
- ¿Los secretos permanecen fuera del código y de los artefactos?
- ¿Hay cambios de dependencias o configuración que requieran revisar advisories, SAST, permisos o superficie de exposición?
- No inventes explotabilidad ni CVEs; diferencia riesgo confirmado de pregunta pendiente.

### Calidad
- ¿El cambio es acotado, coherente con los patrones del proyecto y suficientemente legible?
- ¿Se manejan correctamente errores, estados límite, concurrencia y compatibilidad cuando aplican?
- ¿Se introducen duplicación, complejidad o efectos laterales evitables?

### Pruebas y CI
- ¿Las pruebas cubren caminos felices, errores, bordes y regresiones relevantes al cambio?
- ¿Hay evidencia real de build, pruebas y checks de CI del commit del PR?
- ¿Los gates están habilitados y pasan, sin skips o bypass no justificados?
- No declares pruebas, CI ni cobertura ejecutadas si no puedes verificar su resultado. Indica los gates pendientes o sin evidencia.

### Documentación
- ¿Se actualizan contratos, configuración, operación, migraciones o guías de usuario afectados?
- ¿La descripción del PR explica propósito, comportamiento preservado, riesgos y decisiones?
- ¿Las instrucciones y ejemplos coinciden con el comportamiento del código?

### Versionamiento
- ¿Se actualiza versión, changelog, etiqueta o metadato de release cuando lo requieren las convenciones del repositorio?
- ¿La versión mantiene compatibilidad con el cambio y evita publicar una versión incompatible como patch/minor sin justificación?
- Si no hay una política de versionamiento o el PR no es un release, márcalo como no aplicable y explica por qué.

## Formato de salida

### Hallazgos

Lista cada hallazgo con:
- **Severidad:** crítica, alta, media o baja.
- **Evidencia:** archivo y línea(s), o check verificable.
- **Problema e impacto:** defecto observable o riesgo concreto introducido por el PR.
- **Recomendación:** corrección mínima que resuelva el problema.

Si no se encontraron hallazgos, escribe: “No encontré defectos accionables en el alcance revisado.”

### Checklist

Para cada categoría (Contratos, Seguridad, Calidad, Pruebas y CI, Documentación, Versionamiento), indica **Cumple**, **Falta evidencia**, **No aplica** o **Requiere seguimiento**, con evidencia o motivo breve.

### Límites

Indica archivos, checks, entornos o dependencias que no pudieron verificarse. Distingue explícitamente revisión estática de validación ejecutada.

## Reglas

- No modifiques el PR ni el repositorio durante una revisión.
- No apruebes, solicites cambios en GitHub, cierres ni fusiones el PR, salvo petición explícita separada.
- No marques una categoría como cumplida por intuición o por evidencia de otra rama o commit.
- No incluyas secretos, tokens ni datos sensibles en la respuesta.
- Mantén el análisis limitado al alcance y a defectos sustentados por evidencia.
