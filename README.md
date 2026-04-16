# University OS — Backend

A self-contained Spring Boot backend for the University OS platform. Built with Java 21, Spring Security, PostgreSQL, Ollama (local LLM), and Retell AI.

## ⚙️ Prerequisites

Before deploying, ensure the following are installed on your server/machine:

| Requirement | Version | Notes |
|---|---|---|
| **Ollama** | Latest | Running locally for AI agents |

> [!NOTE]
> You do **not** need Maven, IntelliJ, or Eclipse. The Maven wrapper (`mvnw`) is bundled, and our **Automated Startup Scripts** handle environment verification and building for you.

### 📥 Java Download Guide (Adoptium.net)
If you don't have Java 21, follow these steps for the cleanest experience:
1. **Go to**: [Adoptium.net (Java 21)](https://adoptium.net/temurin/releases/?version=21&os=windows&arch=x64)
2. **Download**: Click the **.msi** button for Windows x64.
3. **Install**: Run the installer and click **Next**.
4. **Crucial Step**: When the "Custom Setup" screen appears, ensure you select **"Will be installed on local hard drive"** for:
   - `Add to PATH`
   - `Set JAVA_HOME variable`
5. **Verify**: Restart your terminal and type `java -version`.

> [!TIP]
> **Pro Way (Windows):** Open PowerShell as Administrator and run:
> `winget install eclipse.temurin.21.jdk`

---

## 🔑 Configuration & Secrets

The system is designed for "plug-and-play" deployment. It supports environment variables for all sensitive configuration. If a `secrets.properties` file is missing, the app will gracefully boot using the environment defaults below.

| Variable | Description | Default |
|---|---|---|
| `DB_PASSWORD` | PostgreSQL password for `postgres` user | `password` |
| `MAIL_USERNAME` | Automated email sender address | **(required)** |
| `MAIL_PASSWORD` | 16-character Gmail App Password | **(required)** |
| `FRONTEND_URL` | URL of the frontend app | `http://localhost:5173` |
| `APP_DB_AUTO_SEED`| Seed DB on first run (`always` / `never`) | `never` |

---

## 🗄️ Database Setup (Fresh System)

1. **Create the DB**: Log into your PostgreSQL instance and run:
   ```sql
   CREATE DATABASE unios;
   ```
2. **First-Time Seeding**: To automatically populate the database with required schema and seed data, set the auto-seed variable to `always` for the first run:

   **Windows (PowerShell):**
   ```powershell
   $env:APP_DB_AUTO_SEED="always"
   .\start.ps1
   ```

   **Linux/Mac (Bash):**
   ```bash
   export APP_DB_AUTO_SEED=always
   ./start.sh
   ```

3. **Subsequent Runs**: After the first successful boot, you can clear the variable or set it to `never`. The system will retain all existing data.

---

## 🚀 How to Run

### Automated Startup (Recommended)

Our startup scripts perform a **5-step health check** to ensure a smooth boot:
1. **Java Verification**: Ensures Java 21+ is in your PATH.
2. **LLM Connection**: Verifies Ollama is running (and starts it if not).
3. **Model Management**: Automatically pulls `llama3.2` if it's missing.
4. **Automated Build**: Compiles the project using the bundled Maven wrapper.
5. **Clean Boot**: Activates the server with optimized, low-noise logging.

**Windows:**
```powershell
.\start.ps1
```

**Linux / macOS:**
```bash
chmod +x start.sh
./start.sh
```

---

## 🛠️ Troubleshooting

- **`git clone` RPC failed**: The repository history may be large. Increase your Git buffer by running:
  `git config --global http.postBuffer 524288000`
- **`java` not recognized**: Ensure you have installed Java 21 and restarted your terminal.
- **`ollama` not recognized**: Ensure Ollama is installed. If on Windows, restart your terminal after installation so the PATH is updated.
- **Port 8080 already in use**: Another app is running on the default port. You can kill the process or change the port in `application.properties`.
- **Database Connection Refused**: Ensure PostgreSQL is running and you have created the `unios` database.

---

## 📡 API Testing
Once the server is running, you can verify it with Postman:
- **Health Check**: `GET http://localhost:8080/api/universities`
- **Expected Outcome**: Should return a `200 OK` with a JSON array of universities.

---

## 🏗️ Architecture

```
src/
├── main/
│   ├── java/com/unios/
│   │   ├── config/         # Security, JWT, CORS, and Property config
│   │   ├── controller/     # REST controllers
│   │   ├── service/        # Business logic & AI agents
│   │   ├── repository/     # JPA data repositories
│   │   └── model/          # Entity classes
│   └── resources/
│       ├── application.properties
│       └── db/             # Optimized SQL seed data (unios_dump.sql)
```
