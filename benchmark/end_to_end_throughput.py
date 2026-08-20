import json
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

DONATION_URL = "http://localhost:8081/lots"
REQUEST_URL = "http://localhost:8082/requests"
METRICS_URL = "http://localhost:8083/actuator/metrics/matching.allocations.created"

PAIR_COUNT = 30
QUANTITY = 10
WORKER_THREADS = 50
POLL_TIMEOUT_SECONDS = 240
HTTP_TIMEOUT_SECONDS = 10


def post(url, payload):
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    urllib.request.urlopen(request, timeout=HTTP_TIMEOUT_SECONDS).read()


def create_lot(_):
    expiry = (datetime.now(timezone.utc) + timedelta(days=30)).isoformat().replace("+00:00", "Z")
    post(DONATION_URL, {
        "donorId": str(uuid.uuid4()),
        "itemType": "CANNED_VEGETABLES",
        "quantityTotal": QUANTITY,
        "expiryDate": expiry,
    })


def create_request(_):
    post(REQUEST_URL, {
        "shelterId": str(uuid.uuid4()),
        "itemType": "CANNED_VEGETABLES",
        "quantityRequested": QUANTITY,
    })


def read_created_count():
    try:
        with urllib.request.urlopen(METRICS_URL) as response:
            body = json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return 0
        raise
    for measurement in body["measurements"]:
        if measurement["statistic"] == "COUNT":
            return int(measurement["value"])
    return 0


def main():
    print("Reading baseline allocation count...", flush=True)
    baseline = read_created_count()
    print(f"Baseline: {baseline}", flush=True)

    start = time.perf_counter()
    print(f"Creating {PAIR_COUNT} lots...", flush=True)
    with ThreadPoolExecutor(max_workers=WORKER_THREADS) as executor:
        list(executor.map(create_lot, range(PAIR_COUNT)))
    print(f"Creating {PAIR_COUNT} requests...", flush=True)
    with ThreadPoolExecutor(max_workers=WORKER_THREADS) as executor:
        list(executor.map(create_request, range(PAIR_COUNT)))
    print("All create calls returned, polling for matches...", flush=True)

    target = baseline + PAIR_COUNT
    deadline = time.perf_counter() + POLL_TIMEOUT_SECONDS
    current = baseline
    while time.perf_counter() < deadline:
        current = read_created_count()
        print(f"  matched so far: {current - baseline} / {PAIR_COUNT}", flush=True)
        if current >= target:
            break
        time.sleep(1)
    elapsed = time.perf_counter() - start

    matched = current - baseline
    print(f"Created {PAIR_COUNT} lot/request pairs")
    print(f"Matched allocations observed: {matched} / {PAIR_COUNT}")
    print(f"Elapsed time: {elapsed:.2f}s")
    print(f"Throughput: {matched / elapsed:.2f} matches/sec")


if __name__ == "__main__":
    main()
