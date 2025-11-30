# Toko

A EntJava2 Project by M G T

## Instructions

### 1. Docker Compose (run first)
- Ensure Docker and Docker Compose are installed and running.
- From the project root (where `docker-compose.yml` lives):
    - Build and start services: `docker-compose up -d`
    - Stop and remove services: `docker-compose down`

### 2. Run the Spring Boot app (after Docker Compose is up)
- Terminal:
    - Maven wrapper: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`)
    - Or with installed tools: `mvn spring-boot:run`
- IntelliJ:
    - Open/import the project as Maven.
    - Locate the main class annotated with `@SpringBootApplication`.
    - Click the Run icon or create an "Application" run configuration and start it.

## 3. Notes
- Default app URL: `http://localhost:8090` (port may differ based on configuration).
- Start Docker Compose before starting the app so required services (DB, cache, etc.) are available.