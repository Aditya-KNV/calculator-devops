# Calculator REST API — End-to-End CI/CD Pipeline

CSE 816 Software Production Engineering — Mini-Project
Java 17 · Spring Boot 3.5 · Maven · JUnit 5 · Git/GitHub · Jenkins · Docker · Docker Compose

A push to GitHub automatically builds, tests, containerizes, pushes and deploys the API:

```
git push ─► GitHub ─(webhook)─► Jenkins (WSL2)
   Checkout ─► Build ─► Test ─► Docker Build ─► Push ─► Deploy + verify ─► Email
                                                  │          │
                                             Docker Hub   docker compose:
                                                          nginx :8081 ─► calculator-api ×2
```

## 1. API

| Operation | Endpoint                     | Behaviour |
|-----------|------------------------------|-----------|
| Add       | `GET /api/v1/add?a=&b=`      | a + b |
| Subtract  | `GET /api/v1/subtract?a=&b=` | a − b |
| Multiply  | `GET /api/v1/multiply?a=&b=` | a × b |
| Divide    | `GET /api/v1/divide?a=&b=`   | a ÷ b; `b=0` → HTTP 400 |
| Health    | `GET /actuator/health`       | `{"status":"UP"}` |

```bash
$ curl "http://localhost:8080/api/v1/add?a=2&b=3"
{"operation":"add","a":2.0,"b":3.0,"result":5.0}

$ curl "http://localhost:8080/api/v1/divide?a=1&b=0"          # HTTP 400
{"timestamp":"2026-10-21T04:54:50Z","status":400,"error":"Bad Request",
 "message":"Division by zero is not allowed","path":"/api/v1/divide"}
```
HTTP 400 is also returned for a missing parameter (`Missing required parameter 'b'`), a non-numeric
value (`Parameter 'a' must be a valid number`), `NaN`/`Infinity` inputs, and results that overflow.

## 2. Build, test, run

```bash
./mvnw clean package          # build executable JAR -> target/calculator-api.jar (runs tests)
./mvnw test                   # tests only; JUnit XML in target/surefire-reports, coverage in target/site/jacoco
java -jar target/calculator-api.jar               # http://localhost:8080

docker build -t calculator-api .                  # single container
docker run -d -p 8080:8080 calculator-api

docker compose up -d --build                      # full stack: nginx + 2 API replicas -> http://localhost:8081
docker compose ps
docker compose down
```
On Windows use `mvnw.cmd`. Requires only JDK 17+ (the Maven Wrapper downloads Maven).

**Tests:** `CalculatorServiceTest` (JUnit 5 unit tests for every operation incl. divide-by-zero),
`CalculatorControllerTest` (MockMvc integration tests incl. validation errors),
`CalculatorApplicationTests` (full app on a random port over HTTP).

## 3. Repository layout

```
├── src/main/java/com/spe/calculator/
│   ├── controller/CalculatorController.java   REST endpoints
│   ├── service/CalculatorService.java         arithmetic + validation
│   ├── exception/GlobalExceptionHandler.java  errors -> structured 400 JSON
│   └── model/                                 response records
├── src/test/java/...                          JUnit 5 + MockMvc tests
├── pom.xml                                    Maven build, JaCoCo coverage
├── Dockerfile                                 multi-stage (Maven/JDK -> JRE alpine, non-root)
├── .dockerignore
├── docker-compose.yml                         nginx reverse proxy + 2 API replicas
├── nginx/default.conf                         proxy / load-balancer config
├── Jenkinsfile                                declarative CI/CD pipeline
└── .gitignore, .gitattributes
```

## 4. Docker & Compose design

- **Dockerfile:** stage 1 (`maven:3.9.11-eclipse-temurin-17`) copies `pom.xml` first and resolves dependencies
  so that layer is cached; stage 2 (`eclipse-temurin:17-jre-alpine`) copies only the JAR, runs as the
  non-root `spring` user, `EXPOSE 8080`, has a `HEALTHCHECK` on `/actuator/health`.
- **Compose:** project `calculator-devops`, bridge network `calculator-net`.
  - `calculator-api` — 2 replicas, not published to the host, health check, `restart: unless-stopped`,
    env vars (`SERVER_PORT`, `APP_ENV`, `JAVA_OPTS`, `TZ`).
  - `nginx` — the only public entry point (`${HOST_PORT:-8081}:80`), starts after the API is healthy,
    round-robins across replicas via Docker DNS. The `X-Upstream-Server` response header shows which replica answered.

## 5. Jenkins setup (WSL2 Ubuntu)

1. **Agent prerequisites:** JDK 17, Docker Engine, the **Compose v2 plugin** (`sudo apt install docker-compose-plugin`),
   and `sudo usermod -aG docker jenkins && sudo systemctl restart jenkins`.
2. **Plugins:** Pipeline, Git, GitHub, Credentials Binding, JUnit, Email Extension, Timestamper.
3. **Docker Hub credential:** *Manage Jenkins → Credentials → Global → Add* → Username with password,
   ID `dockerhub-credentials`, password = a Docker Hub **access token**.
4. **Email:** *Manage Jenkins → System → Extended E-mail Notification*: SMTP `smtp.gmail.com`, port `465`,
   *Use SSL*, credentials = Gmail address + a Gmail **App Password**. Set the same in *E-mail Notification*
   and use *Test configuration* to confirm.
5. **Job:** *New Item → Pipeline* → *Pipeline script from SCM* → Git → repo URL, branch `*/main`,
   Script Path `Jenkinsfile`. Tick *GitHub hook trigger for GITScm polling*. Run *Build with Parameters*
   once and set `DOCKERHUB_USER`.
6. **Webhook:** WSL Jenkins is not publicly reachable, so expose it: `ngrok http 8080`, then GitHub repo
   *Settings → Webhooks* → `https://<id>.ngrok-free.app/github-webhook/`, `application/json`, push events.
   `pollSCM` (every 5 min) remains as a fallback.

### Pipeline stages

| Stage        | Action |
|--------------|--------|
| Checkout     | `checkout scm` of the pushed commit |
| Build        | `./mvnw clean package -DskipTests` |
| Test         | `./mvnw test`; JUnit results published; build fails on any test failure; JAR + coverage archived |
| Docker Build | `docker build` tagged `:<BUILD_NUMBER>` and `:latest` |
| Push         | `docker login` with Jenkins credentials (token never in the repo), push both tags |
| Deploy       | `docker compose up -d --wait` (recreates only changed containers, waits for health), then curls health, `add` and divide-by-zero through Nginx |
| post         | HTML email on success/failure (log attached on failure), prune dangling images |

**Idempotency:** re-running a build doesn't tear the stack down — `compose up -d` converges the running
stack to the desired state; an unchanged image means no container is recreated.

## 6. Links

- GitHub: `https://github.com/Aditya-KNV/calculator-devops`
- Docker Hub: `https://hub.docker.com/r/aditya6033/calculator-api`

_Pipeline trigger test._
