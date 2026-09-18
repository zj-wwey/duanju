#!/usr/bin/env python3
"""Diagnose R2 presigned URL PUT failure - pass token directly."""
import requests
import json
import sys

if len(sys.argv) < 2:
    print("Usage: python3 test-r2.py <token>")
    sys.exit(1)

TOKEN = sys.argv[1]
BASE = "http://localhost:8080"

# 1. Get presigned URL
r = requests.post(f"{BASE}/api/admin/storage/presign?fileName=test.mp4&type=video&contentType=video/mp4",
                   headers={"Authorization": f"Bearer {TOKEN}"})
presign_data = r.json()
print(f"Presign status: {r.status_code}")
print(f"Presign response: {json.dumps(presign_data, indent=2)}")

presigned_url = presign_data.get("data", {}).get("presignedUrl", "")
if not presigned_url:
    presigned_url = presign_data.get("data", {}).get("url", "")
if not presigned_url:
    print("ERROR: No presigned URL!")
    sys.exit(1)

print(f"\nPresigned URL: {presigned_url[:150]}...")

# 2. PUT to R2 with Content-Type
print("\n=== PUT to R2 (with Content-Type) ===")
try:
    r = requests.put(presigned_url, data=b"hello-test-content", headers={"Content-Type": "video/mp4"}, timeout=30)
    print(f"Status: {r.status_code}")
    print(f"Headers: {dict(r.headers)}")
    print(f"Body:\n{r.text}")
except Exception as e:
    print(f"Error: {e}")

# 3. PUT without Content-Type
print("\n=== PUT to R2 (no Content-Type) ===")
try:
    r2 = requests.put(presigned_url, data=b"hello-test-content", timeout=30)
    print(f"Status: {r2.status_code}")
    print(f"Headers: {dict(r2.headers)}")
    print(f"Body:\n{r2.text}")
except Exception as e:
    print(f"Error: {e}")
