# FileFlow

A small managed-file-transfer service built with Java 17 and Spring Boot.
You define SFTP transfer jobs through a REST API, run them on demand or on a
cron schedule, and every execution is recorded in an audit log.

I built it to understand how enterprise file-transfer and workload-automation
tools work underneath, after a year of configuring SFTP integrations as a
consultant intern.

## Features

- REST API to create, update, pause, resume and delete transfer jobs
- Inbound (download) and outbound (upload) SFTP transfers of a whole directory
- Cron scheduling per job, kept in sync when jobs change
- Retries with exponential backoff (2s, 4s, 8s) for network errors; no retry
  for a broken job definition
- SHA-256 checksum verification of every file; files are written as `.part`
  and renamed only after the checksum matches
- Audit log: every run has a status (QUEUED, RUNNING, SUCCESS, FAILED),
  timestamps, bytes transferred and a message
- A job cannot run twice at the same time
- Input validation and consistent JSON error responses
- OpenAPI/Swagger UI

## Tech

Java 17, Spring Boot 4, Spring Data JPA, H2, sshj, JUnit 5, Mockito, Maven

## Run it

Needs JDK 17+, Maven and Docker.

```bash
# a local SFTP server (user: demo, password: demo123)
docker run -d --name fileflow-sftp -p 2222:22 \
  -v /path/to/server-files:/home/demo/upload \
  atmoz/sftp demo:demo123:::upload

export FILEFLOW_SFTP_PASSWORD=demo123
mvn spring-boot:run
```

Open http://localhost:8080/swagger-ui.html.

Example job:

```json
{
  "name": "Pull invoices",
  "host": "localhost",
  "port": 2222,
  "username": "demo",
  "remotePath": "/upload",
  "localPath": "/data/downloads",
  "direction": "INBOUND",
  "cronExpression": "0 */5 * * * *"
}
```

## API

| Method | Path | Description |
|---|---|---|
| POST | /api/jobs | Create a job |
| GET | /api/jobs | List jobs |
| GET | /api/jobs/{id} | Get a job |
| PUT | /api/jobs/{id} | Update a job |
| DELETE | /api/jobs/{id} | Delete a job and its runs |
| POST | /api/jobs/{id}/pause | Stop scheduled runs |
| POST | /api/jobs/{id}/resume | Resume a paused job |
| POST | /api/jobs/{id}/runs | Trigger a run now |
| GET | /api/jobs/{id}/runs | Run history, newest first |

## Tests

```bash
mvn test
```

## Known limitations

- No known_hosts file means the app accepts any SFTP host key (development only)
- Passwords come from one environment variable, not per job
- H2 is in-memory, so data is lost on restart
- No authentication on the API yet
