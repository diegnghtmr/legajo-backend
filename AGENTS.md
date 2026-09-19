# backend — Legajo API

Servidor de Legajo. Implementa a mano seis capacidades de similitud
textual, cuatro criterios de agrupamiento jerárquico y sus métricas internas
sobre los abstracts del corpus, y los expone por una API REST. Este archivo dice
dónde está cada cosa y cómo se trabaja aquí. Las reglas de producto, las
fórmulas y los criterios de aceptación viven en la documentación del espacio de
trabajo (`../docs/`), que no forma parte de este repositorio y nunca se sube.

Si una herramienta busca `CLAUDE.md`, se le deja un puntero de una línea a este
archivo, nunca una copia.

## Primero

| Antes de tocar… | Leer |
|---|---|
| Un algoritmo de similitud, un enlace o una métrica | TRD §6.3, §6.4 y §6.5, más las pruebas doradas de §13 |
| El preprocesado de texto | TRD §6.2: cinco pasos exactos, sin filtro por longitud, Porter apagado por defecto |
| Un endpoint o un campo de respuesta | TRD §6.6 y `docs/openapi-legajo.yaml` |
| Los archivos de datos o la ingesta | TRD §6.1 y §9 |
| El despliegue, CORS o una variable de entorno | TRD §14 |
| Qué se acepta como terminado | TRD §15 (TAC-01..TAC-20) y PRD §6 (HU) |
| Qué puede hacer una biblioteca y qué no | TRD §3.3 (frontera R-02) |

## Pila

| Componente | Decisión |
|---|---|
| Java | 25 LTS. Sin características en vista previa como dependencia |
| Spring Boot | 4.0.x (Spring Framework 7, JUnit 6) |
| Build | Gradle 9.2.x, Kotlin DSL, multimódulo |
| Arquitectura | Hexagonal: `domain` → `application` → `infrastructure` → `bootstrap`. ArchUnit la vigila y hace fallar el build |
| Persistencia | Sin base de datos. `data/corpus.json` y `data/embeddings-*.json` versionados en git |
| Embeddings | `all-MiniLM-L6-v2` local (DJL/ONNX, precómputo offline) y `text-embedding-3-small` por API (Spring AI). En la demo, ambos desde caché |
| Contrato | `docs/openapi-legajo.yaml` es la fuente de verdad; springdoc lo publica |
| Imagen | `eclipse-temurin:25-jdk`, Docker Compose con perfiles `default` e `ingest` |

## Mapa del repositorio

| Ruta | Qué va ahí | Qué no va ahí |
|---|---|---|
| `domain/` | Algoritmos, preprocesado, métricas, puertos, `stopwords-en.txt` como recurso. Java puro | Spring, Jackson, DJL, nada de infraestructura |
| `application/` | Casos de uso y servicios que orquestan (`SimilarityService`, `ClusteringService`, `EvaluationService`, `CorpusService`) | Lógica de algoritmos |
| `infrastructure/` | Adaptadores: REST, PDF (GROBID y PDFBox), embeddings (DJL, OpenAI), corpus JSON, caché Caffeine | Reglas de negocio |
| `bootstrap/` | Composición de Spring, configuración, perfiles, arranque | Lógica de dominio |
| `benchmarks/` | Arnés JMH: curvas empíricas frente a la complejidad teórica | Pruebas unitarias |
| `data/` | `corpus.json`, `embeddings-minilm.json`, `embeddings-openai.json` (versionados); `pdfs/` (ignorado por git) | Ediciones a mano |
| `docs/` | Solo `openapi-legajo.yaml` | PRD, TRD, `DESIGN.md`: viven en `../docs/` |
| `docker/`, `docker-compose.yml` | Imagen y perfiles | Secretos |
| `.github/workflows/` | CI: build, ArchUnit, JaCoCo, smoke de la imagen | — |

Dónde vive cada algoritmo, bajo el paquete raíz `co.edu.uniquindio.legajo`:

| Cosa | Paquete de `domain` |
|---|---|
| `levenshtein`, `needleman-wunsch`, `jaccard`, `tfidf-cosine`, `embedding-local`, `embedding-api` | `similarity`: una clase por capacidad, jerarquía `sealed`, registro por lista de Spring en `infrastructure` |
| Motor Lance–Williams y los cuatro enlaces | `clustering`: `SingleLinkage`, `CompleteLinkage`, `AverageLinkage`, `WardLinkage` |
| Correlación cofenética, silueta media, Davies–Bouldin | `evaluation` |
| NFC, minúsculas, tokens, stopwords, Porter opcional | `preprocess` |
| Puerto de embeddings, puerto de corpus | `port` |

Si el código ya existe y difiere de este mapa, el código manda en ubicación y el
TRD en comportamiento. El mapa se actualiza en el mismo pull request.

## Comandos

Definidos por el TRD §13 y §14; `./gradlew tasks` confirma los nombres reales.

| Para | Comando |
|---|---|
| Compilar, pruebas y ArchUnit | `./gradlew build` |
| Solo pruebas | `./gradlew test` |
| Cobertura agregada (más del 85 % en los paquetes de algoritmos) | `./gradlew jacocoRootReport` |
| Arrancar en local sin red | `LEGAJO_EMBEDDING_PROVIDER=cached ./gradlew :bootstrap:bootRun` |
| Demo completa (backend `:8080`, frontend `:80`) | `docker compose up` |
| Ingesta de PDF, una sola vez, con GROBID | `docker compose --profile ingest up` |
| Verificar el corpus después de la ingesta | script `verify-corpus` (TRD §6.1) |
| Precalcular embeddings tras una ingesta | tarea de precómputo (TRD §6.1 y §8); deja `corpusSha256` en las cachés |
| Curvas de complejidad | tarea JMH del módulo `benchmarks` |
| Salud | `GET /actuator/health` |

Variables de entorno: `LEGAJO_EMBEDDING_PROVIDER` (`cached` en la demo),
`LEGAJO_CORS_ORIGINS`, `SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_OPENAI_API_KEY`.
`.env.example` las documenta; los valores nunca se suben.

## Reglas que no se negocian

1. **R-02.** Levenshtein, Needleman–Wunsch, Jaccard, TF-IDF y coseno, la
   métrica de embeddings, el preprocesado, Lance–Williams, la cofenética, la
   silueta y Davies–Bouldin se escriben a mano con estructuras básicas del
   lenguaje. Delegable: parseo de PDF, inferencia del modelo de embeddings,
   marco web, JSON, caché, empaquetado. La tabla exacta está en TRD §3.3.
2. **Doble precisión.** Toda métrica se calcula en `double`; cada vector de
   embeddings se renormaliza a norma unitaria al cargarse; la tolerancia de
   1e-9 se sostiene por eso, no por el formato de almacenamiento (TRD §6.3).
3. **Determinismo.** Misma entrada, misma salida bit a bit. Desempates fijos:
   en la matriz DP diagonal, arriba, izquierda; en el agrupamiento el par
   lexicográficamente menor (TRD §6.3 y §6.4).
4. **Sin estado entre llamadas.** `POST /clustering` devuelve enlaces y
   evaluación en la misma respuesta; nada depende de una llamada anterior.
5. **`domain` sin frameworks.** ArchUnit lo comprueba en cada build.
6. **El contrato primero.** OpenAPI cambia antes que el código; el frontend
   regenera sus tipos y la CI falla si hay deriva.
7. **Datos generados, no editados.** `corpus.json` y `embeddings-*.json` salen
   de la ingesta y del precómputo. La caché queda ligada al corpus por
   `corpusSha256` y el arranque falla si no coincide.
8. **Constantes fijadas.** Needleman–Wunsch usa +1, −1, −1 y no es editable;
   TF-IDF calcula `df` y `N` sobre el corpus completo; Ward consume `2·D`.
   Cambiarlas es cambiar el TRD, no el código.

## Pruebas: primero la prueba

TDD estricto: rojo, verde, refactor. Sin prueba no hay código nuevo.

| Nivel | Herramienta | Qué cubre |
|---|---|---|
| Unitarias parametrizadas | JUnit 6 y AssertJ | Valores conocidos: `kitten`/`sitting` = 3 en el núcleo DP por caracteres; doradas por tokens; NW en secuencias pequeñas; Jaccard disjunto = 0 y casos vacíos |
| Propiedades | jqwik | Simetría, identidad, rango [0, 1], alturas monótonas de los enlaces |
| Doradas | JUnit | Matrices de enlace con n = 5 calculadas a mano; caminos DP |
| Arquitectura | ArchUnit | Dominio sin frameworks; dirección de las capas |
| Integración | Testcontainers | Cliente GROBID (perfil `ingest`) |
| Adaptador remoto | WireMock | OpenAI: éxito, 5xx, tiempo de espera, mapeo a 503 |
| Cobertura | JaCoCo agregado | Más del 85 % en `similarity`, `clustering`, `evaluation` |
| Rendimiento | JMH | Curvas frente a la complejidad teórica, con el arnés de referencia documentado |

Los valores dorados y las pruebas obligatorias por algoritmo están en TRD §13;
los casos degenerados (secuencias o conjuntos vacíos) en TRD §6.3.

## Commits y pull requests

- Conventional Commits en inglés con alcance por módulo o dominio:
  `feat(similarity): …`, `fix(clustering): …`, `test(domain): …`,
  `docs(api): …`, `chore(build): …`. Sin atribución a IA.
- Una rama por unidad de trabajo. PR de hasta 400 líneas; si crece, se
  encadena (skill `chained-pr`).
- Cada PR lleva sus pruebas, actualiza `openapi-legajo.yaml` si tocó el
  contrato y este archivo si movió algo del mapa.
- Skills: `skill-git-pr-conventional-commits`, `branch-pr`, `work-unit-commits`.

## Nunca

- Subir `.env`, claves, los PDF del docente, ni nada de `../docs/`.
- Traer una biblioteca que implemente un algoritmo pedido (R-02).
- Meter Spring, Jackson o DJL en `domain`.
- Editar a mano `corpus.json` o `embeddings-*.json`.
- Cambiar una fórmula, una constante o un campo del contrato sin actualizar
  antes el TRD y el OpenAPI.
- Dar por terminado un cambio sin pruebas en verde y sin ArchUnit en verde.

## Siempre

- Buscar la regla en el TRD antes de decidir; si no está, preguntar al autor.
- Escribir la prueba antes del código y correr `./gradlew build` antes del commit.
- Mantener el mapa de este archivo al día cuando se mueve algo.

## Si algo no está claro

1. Este archivo.
2. La sección del TRD indicada en «Primero».
3. `../AGENTS.md` del espacio de trabajo, para lo que cruza al frontend.
4. Preguntar al autor. No se asume.
