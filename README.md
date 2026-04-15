# University OS — Backend

A self-contained Spring Boot backend for the University OS platform. Built with Java 21, Spring Security, PostgreSQL, Ollama (local LLM), and Retell AI.

## ⚙️ Prerequisites

Before deploying, ensure the following are installed and running on your server/machine:

| Requirement | Version | Notes |
|---|---|---|
| **Java** | 21+ | `java -version` must work in terminal |
| **PostgreSQL** | 14+ | Running on port `5432` |
| **Ollama** | Latest | For local LLM inference |

> [!NOTE]
> You do **not** need Maven, IntelliJ, or Eclipse. The Maven wrapper (`mvnw`) is bundled and will download everything automatically.

---

## 🔑 Environment Variables

Set the following environment variables on your machine **before** running the server. Defaults are shown in brackets — **mandatory** fields have no safe default.

| Variable | Description | Default |
|---|---|---|
| `DB_PASSWORD` | PostgreSQL password for the `postgres` user | `password` |
| `MAIL_USERNAME` | Gmail address used to send emails | **(required)** |
| `MAIL_PASSWORD` | 16-character Gmail App Password | **(required)** |
| `RETELL_API_KEY` | Retell AI API key | **(required)** |
| `RETELL_AGENT_ID` | Retell AI agent ID | **(required)** |
| `RETELL_FROM_NUMBER` | Retell AI phone number (e.g. `+16362751168`) | **(required)** |
| `OLLAMA_API_URL` | Ollama generate endpoint | `http://localhost:11434/api/generate` |
| `OCR_API_URL` | Ollama OCR endpoint | `http://localhost:11434` |
| `FRONTEND_URL` | URL of the frontend app (used in emails) | `http://localhost:5173` |

### Setting Variables

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD="your_postgres_password"
$env:MAIL_USERNAME="you@gmail.com"
$env:MAIL_PASSWORD="your_16_char_app_password"
$env:RETELL_API_KEY="your_retell_api_key"
$env:RETELL_AGENT_ID="your_retell_agent_id"
$env:RETELL_FROM_NUMBER="+1XXXXXXXXXX"
$env:FRONTEND_URL="http://your-server-ip:5173"
```

**Linux / macOS (Bash):**
```bash
export DB_PASSWORD="your_postgres_password"
export MAIL_USERNAME="you@gmail.com"
export MAIL_PASSWORD="your_16_char_app_password"
export RETELL_API_KEY="your_retell_api_key"
export RETELL_AGENT_ID="your_retell_agent_id"
export RETELL_FROM_NUMBER="+1XXXXXXXXXX"
export FRONTEND_URL="http://your-server-ip:5173"
```

---

## 🗄️ Database Setup

1. Install PostgreSQL and ensure it is running on port `5432`.
2. Create a database named `unios`:
   ```sql
   CREATE DATABASE unios;
   ```
3. The application will automatically create all tables on first boot (`spring.jpa.hibernate.ddl-auto=update`).

**First-time seed (optional):** If you have a pre-populated SQL dump, place it at `src/main/resources/db/unios_dump.sql` and set the following env var before the first run:
```bash
# Linux/Mac
export APP_DB_AUTO_SEED=always

# Windows
$env:APP_DB_AUTO_SEED="always"
```
> Switch back to `never` after the first successful boot.

---

## 🚀 How to Run

### Option A — Native (Recommended)

**Windows:**
```powershell
.\start.ps1
```

**Linux / macOS:**
```bash
chmod +x start.sh
./start.sh
```

The startup script will:
1. Check if Ollama is running and pull `llama3.2` if not present.
2. Compile the Spring Boot application with the Maven Wrapper.
3. Boot the server on `http://0.0.0.0:8080`.

---

### Option B — Docker

```bash
# Build the image
docker build -t unios-backend .

# Run the container with all required env vars
docker run -d \
  -p 8080:8080 \
  -e DB_PASSWORD="your_postgres_password" \
  -e MAIL_USERNAME="you@gmail.com" \
  -e MAIL_PASSWORD="your_16_char_app_password" \
  -e RETELL_API_KEY="your_retell_api_key" \
  -e RETELL_AGENT_ID="your_retell_agent_id" \
  -e RETELL_FROM_NUMBER="+1XXXXXXXXXX" \
  -e FRONTEND_URL="http://your-server-ip:5173" \
  -e OLLAMA_API_URL="http://host.docker.internal:11434/api/generate" \
  -e OCR_API_URL="http://host.docker.internal:11434" \
  --name unios-backend \
  unios-backend
```

> **Note:** When running via Docker, Ollama must be running on the **host machine**. The `host.docker.internal` hostname resolves to the host from inside the container (on Linux, add `--add-host=host.docker.internal:host-gateway`).

---

## 📡 API Overview

Base URL: `http://your-server:8080/api`

| Method | Endpoint | Description | Role |
|---|---|---|---|
| POST | `/auth/login` | Authenticate and get JWT | All |
| POST | `/auth/register` | Register user (requires `ADMIN_SECRET`) | Admin |
| GET | `/users/profile` | Get current user profile | All |
| POST | `/results/upload` | Upload student results | Admin |
| GET | `/schedule/generate` | Generate timetable via OptaPlanner | Admin |

---

## 🏗️ Architecture

```
src/
├── main/
│   ├── java/org/example/
│   │   ├── config/         # Security, JWT, CORS config
│   │   ├── controller/     # REST controllers
│   │   ├── service/        # Business logic & agents
│   │   ├── repository/     # JPA data repositories
│   │   ├── model/          # Entity & DTO classes
│   │   └── agent/          # Ollama-powered AI agents
│   └── resources/
│       ├── application.properties
│       └── db/             # SQL seed files
```
