import requests
import random
import time

BASE_URL = "http://localhost:8080/api"
STUDENT_COUNT = 5000
ELIGIBLE_COUNT = 3500
BATCH_NAME = "Engineering 2026"

print(f"🚀 Initializing High-Scale Admission Scenario: {BATCH_NAME}")

batch_payload = {
    "name": BATCH_NAME,
    "batchCode": "ENG-2026",
    "startYear": 2026,
    "endYear": 2030,
    "duration": 4,
    "status": "ADMISSIONS_OPEN",
    "seatCapacity": 3000,
    "waitlistCapacity": 300,
    "minAcademicCutoff": 60.0
}

try:
    resp = requests.post(f"{BASE_URL}/batches", json=batch_payload)
    batch_data = resp.json()
    batch_id = batch_data.get('id', 1)
    print(f"✅ Batch Ready: ID {batch_id}")
except Exception as e:
    print(f"⚠️ Batch may already exist or error: {e}")
    batch_id = 1

def seed_admission(i):
    if i <= ELIGIBLE_COUNT:
        score = round(random.uniform(85.0, 99.0), 2)
    else:
        score = round(random.uniform(40.0, 59.0), 2)
        
    payload = {
        "fullName": f"Aspirant {i}",
        "email": f"aspirant_{i}@uni-test.edu",
        "academicScore": score,
        "documentsVerified": True,
        "status": "SUBMITTED",
        "batch": {"id": batch_id}
    }
    try:
        requests.post(f"{BASE_URL}/applications", json=payload)
        return True
    except:
        return False

print(f"-> Injecting {STUDENT_COUNT} applications...")
for i in range(1, STUDENT_COUNT + 1):
    seed_admission(i)
    if i % 100 == 0:
        print(f"   [{i}/{STUDENT_COUNT}] injected...")

print(f"✅ Injection Complete.")
print("\n🔥 NEXT STEPS:")
print("1. Wait for the 'System Heartbeat' (30s pulses) to autonomously trigger screening and ranking.")
print("2. Run 'python bulk_enroll.py' to enroll 2800 students once the heartbeat finishes ranking.")
