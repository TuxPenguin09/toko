# Toko

A EntJava2 Project by **Moritz Chester Saribay**, **Geoff Ronyl Orosco** and **Takuya Nakasone**

[Statolumn](https://statolumn.com) clone (portfolio project by M) but rewritten for simplier and lightweight use.

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

## Postman

You can test the app and the media service with Postman (or curl). A Postman collection is included at `postman/toko.postman_collection.json` which contains example requests for common flows.

Quick manual examples (curl):

- Register (form POST):

```bash
curl -X POST -F "username=alice" -F "password=secret123" -F "confirm=secret123" http://localhost:8090/register -i
```

- Login (form POST):

```bash
curl -X POST -F "username=alice" -F "password=secret123" -c cookies.txt http://localhost:8090/login -i
```

- Create a post with an image (submit as the logged-in user)

```bash
# use the session cookie saved from login
curl -X POST -b cookies.txt -F "content=Hello from curl" -F "file=@/path/to/image.jpg" http://localhost:8090/user/1/post -i
```

Notes:
- Posts and most user interactions are handled by the Thymeleaf web UI, but the media upload goes to the separate media service at `http://localhost:8092/api/media/upload` (the app will call it when you attach a file via the form).
- Import the included Postman collection (`postman/toko.postman_collection.json`) into Postman to get ready-made requests.
