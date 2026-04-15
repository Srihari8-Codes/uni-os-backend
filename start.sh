#!/bin/bash
# start.sh
# -------------------------------------------------------------
# Startup script for UniOS Backend (Linux / Mac)
# Automatically checks for Java, Ollama, builds the JAR, and runs it
# -------------------------------------------------------------

set -e

echo -e "\e[36m=============================\e[0m"
echo -e "\e[36m Starting UniOS Ecosystem\e[0m"
echo -e "\e[36m=============================\e[0m"

# 1. Environment Check (Java 21+)
echo -e "\e[33m[1/5] Verifying Prerequisites...\e[0m"
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    echo -e "-> \e[32mJava version: $JAVA_VER\e[0m"
else
    echo -e "\e[31mCRITICAL ERROR: Java is not installed or not in PATH.\e[0m"
    echo -e "Download Java 21+ from: https://adoptium.net/"
    exit 1
fi

# 2. Verify and start Ollama
echo -e "\e[33m[2/5] Checking LLM Subsystem...\e[0m"
if command -v ollama >/dev/null 2>&1; then
    if pgrep -x "ollama" > /dev/null
    then
        echo "-> Ollama server is already running."
    else
        echo "-> Starting local Ollama server in background..."
        nohup ollama serve > /dev/null 2>&1 &
        sleep 3
    fi

    echo "-> Verifying llama3.2 is available..."
    if ! ollama list | grep -q "llama3.2"; then
        echo "-> Pulling llama3.2 (this may take a while)..."
        ollama pull llama3.2
    else
        echo -e "\e[32m-> llama3.2 is ready.\e[0m"
    fi
else
    echo -e "\e[33mWARNING: Ollama is not installed or not in PATH.\e[0m"
    echo -e "Download it from: https://ollama.com/"
    echo "Skipping LLM checks."
fi

# 3. Build Backend using Maven Wrapper
echo -e "\e[33m[3/5] Building Spring Boot Application...\e[0m"
chmod +x ./mvnw
./mvnw package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "\e[31mCRITICAL ERROR: Build failed. Cannot start server.\e[0m"
    exit 1
fi

# 4. Find and Execute the FAT JAR
echo -e "\e[33m[4/5] Preparing runtime...\e[0m"
JAR_FILE=$(ls target/UniOS-1-*.jar | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo -e "\e[31mCRITICAL ERROR: JAR file not found in target directory.\e[0m"
    exit 1
fi

# 5. Database Reminder (if run for the first time)
if [ "$APP_DB_AUTO_SEED" == "always" ]; then
     echo -e "\e[35m-> NOTICE: Database AUTO-SEED is ACTIVE.\e[0m"
fi

echo -e "\e[33m[5/5] Activating Server...\e[0m"
echo -e "\e[90mPress CTRL+C at any time to shut down.\e[0m\n"
# Run Java process
java -jar "$JAR_FILE"
