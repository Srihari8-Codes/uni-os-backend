# University OS - Backend

The completely self-contained, auto-bootstrapping Spring Boot backend for University OS.

## ⚙️ Prerequisites

Before you download this code and attempt to run it, your server/machine **must** have the following installed:

1. **Java 21 or higher** (`java -version` should work in your terminal)
2. **PostgreSQL** running locally on port 5432
   - Default expected username: `postgres`
   - Create an empty database named: `unios`
3. **Ollama** installed (The backend will automatically start it and download the `llama3.2` model for the agentic systems).

## 🚀 How to Run

You do NOT need to install Maven, Eclipse, or IntelliJ. 

**1. Set Environment Variables**
The backend requires email credentials to function properly (sending exam keys, hall tickets, etc). Run this in your terminal:

*PowerShell (Windows):*
```powershell
$env:MAIL_PASSWORD="your-16-char-gmail-app-password"
$env:DB_PASSWORD="password" # Change if your postgres password is not 'password'
```

*Bash (Linux/Mac):*
```bash
export MAIL_PASSWORD="your-16-char-gmail-app-password"
export DB_PASSWORD="password"
```

**2. Auto-Seed the Database (First Run Only)**
If this is a completely brand new machine with an empty `unios` database, you can automatically run the SQL dump to restore the state:
```powershell
$env:APP_DB_AUTO_SEED="true"
```
*(You only need to do this the very first time. You can skip this step on future runs).*

**3. Run the Startup Script**
Depending on your operating system, simply run:

*Windows:*
```powershell
..\start.ps1
```

*Linux/Mac:*
```bash
../start.sh
```

### What happens?
1. The script will automatically trigger Ollama and ensure the `llama3.2` LLM model is ready.
2. The Maven Wrapper (`mvnw`) will download itself and compile the entire Spring Boot application natively.
3. The server will boot up via the packaged FAT JAR on `localhost:8080`.
4. The frontend will communicate via the dynamic CORS policy perfectly.
