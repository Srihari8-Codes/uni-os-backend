# [DOC] Admission AI Prompt Architecture & Logic

This document maps the **Agentic Logic** of the University OS Admission AI to its specific implementation points in the Java backend.

## 🧠 The "Brain" (ChatAdmissionAgent.java)
The primary orchestrator for the LLM is located in:
`Backend/Uni OS - 1/src/main/java/com/unios/service/agents/admissions/ChatAdmissionAgent.java`

The most critical logic is contained within the **`buildExtractionPrompt(ConversationalStateDTO state)`** method (Lines 154-203). This method dynamically generates the "System Prompt" before every interaction.

### 1. The Persona & Identity
**Code Location**: `ChatAdmissionAgent.java:156`
> **Prompt**: `"You are a Senior Admission Officer at University OS. Your goal is to help students complete their application fluidly."`
> **Reasoning**: This establishes the tone and authority level of the AI.

### 2. Institutional Knowledge (RAG Injection)
**Code Location**: `ChatAdmissionAgent.java:38-50` (Loader) and `157-158` (Injection)
**Source File**: `src/main/resources/config/university-policy.json`
> **Prompt**: `sb.append("INSTITUTIONAL KNOWLEDGE (Your Knowledge Base):\n"); sb.append(this.universityPolicy).append("\n\n");`
> **Reasoning**: This "Knowledge Augmentation" ensures the AI knows the exact cutoffs and policies without needing a separate database query mid-chat.

### 3. State-Aware Context
**Code Location**: `ChatAdmissionAgent.java:160-172`
> **Prompt**: Injecting `state.getSubmittedData()` and `state.getExtractedMarks()`.
> **Reasoning**: This is the "short-term memory." It tells the AI exactly what the student has already sent so it never asks the same question twice.

### 4. Direct "Golden Path" Training (Few-Shot)
**Code Location**: `ChatAdmissionAgent.java:190-201`
> **Prompt**: A series of **Few-Shot Examples** showing perfect input/output pairs.
> **Reasoning**: This is how we "train" the model's behavior. By showing it a "Golden Example" of the your demo message, we ensure it correctly extracts all 3 fields and triggers the completion message in a single turn.

---

## 🛠️ The "Deterministic State Machine"
While the LLM handles the "Chat," the "Truth" is stored in:
`Backend/Uni OS - 1/src/main/resources/config/admission-state-machine.json`

The **`ConversationalAdmissionService.java`** acts as the gatekeeper, ensuring that the AI can only "claim" a field is filled if it matches the strict validations defined in this JSON file.

### Summary for Technical Audit:
- **Architecture**: Retrieval-Augmented Generation (RAG) + State-Machine State Injection.
- **Learning Style**: In-Context Learning (ICL) via Few-Shot Prompting.
- **Constraints**: Forced JSON output schema to bridge the gap between Unstructured Chat and Structured Database records.
