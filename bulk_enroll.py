import requests
import time

BASE_URL = "http://localhost:8080/api"
ENROLL_COUNT = 2800

print(f"🚀 Simulation: Processing enrollment for {ENROLL_COUNT} students...")

try:
    resp = requests.get(f"{BASE_URL}/admissions/batch/1") # Assuming batch ID 1
    candidates = [c for c in resp.json() if c['status'] == 'COUNSELING_PENDING' or c['status'] == 'EXAM_PASSED']
    print(f"-> Found {len(candidates)} candidates waiting for enrollment.")
except Exception as e:
    print(f"❌ Error fetching candidates: {e}")
    candidates = []

successful = 0
for i, candidate in enumerate(candidates[:ENROLL_COUNT]):
    app_id = candidate['id']
    payload = {
        "applicationId": app_id,
        "department": "BS Computer Science", 
        "finalFees": 12000.0,
        "counselorNotes": "Bulk automated ingestion for Engineering 2026."
    }
    try:
        res = requests.post(f"{BASE_URL}/enrollments", json=payload)
        if res.status_code == 200:
            successful += 1
    except:
        pass
    
    if (i + 1) % 100 == 0:
        print(f"   [{i+1}/{ENROLL_COUNT}] students enrolled...")

print(f"✅ Simulation Complete. Enrolled: {successful}/{ENROLL_COUNT}")
print("\n🔥 TRIGGER WAITLIST RECURSION:")
print("The InstitutionalOrchestrator heartbeat will see the vacancy and promote waitlisted students.")
