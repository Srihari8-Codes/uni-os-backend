async function testOllama() {
    const listToString = `[FieldInfoDTO(id=fullName, label=Full Name, type=text, required=true, conversationalPrompt=What is your full name?, options=null, validation=null)]`;
    
    const systemPrompt = `You are an Admission Officer at University OS. Current Phase: PERSONAL_INFO. Remaining required fields: ${listToString}. Instructions: 
1. Extract fields from user input into a JSON object 'extracted_fields'.
2. Craft a polite, conversational reply to the user in 'reply'.
3. If the user provided multiple fields, extract all of them.
4. IMPORTANT: Always return a valid JSON object only.
Return format: { "reply": "...", "extractedFields": { "id": "value" } }`;

    const userPrompt = "I am Srihari";

    try {
        const response = await fetch('http://localhost:11434/api/generate', {
            method: 'POST',
            body: JSON.stringify({
                model: 'llama3.2:3b',
                system: systemPrompt,
                prompt: userPrompt,
                stream: false,
                format: 'json'
            }),
            headers: {'Content-Type': 'application/json'}
        });
        const data = await response.json();
        
        console.log("=== EXACT OLLAMA OUTPUT ===");
        console.log(data.response);
        console.log("===========================");
        
    } catch (e) {
        console.log("ERROR:", e.message);
    }
}

testOllama();
