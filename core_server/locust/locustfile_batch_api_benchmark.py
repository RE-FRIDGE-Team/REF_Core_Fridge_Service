from locust import HttpUser, task, between, events
from locust.runners import MasterRunner, WorkerRunner
import json, random, os

FIXTURE_PATH = os.environ.get(
    "FIXTURE_PATH",
    "/mnt/fixtures/benchmark_fixtures.json"
)

def load_fixtures() -> list[str]:
    try:
        with open(FIXTURE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            items = data.get("items", [])
            print(f"[Locust] 픽스처 로드 완료: {len(items):,}개")
            return items
    except FileNotFoundError:
        fallback = ["삼양 불닭볶음면 140g", "풀무원 두부 300g", "비비고 왕교자 1.05kg"]
        print(f"[Locust] 픽스처 없음, fallback {len(fallback)}개 사용")
        return fallback

FIXTURES: list[str] = load_fixtures()

# ── 분산 환경 제품 처리 수 추적 ──
# 워커 → 마스터로 주기적 전송, 마스터에서 합산
import threading

_local_item_count = 0
_local_lock = threading.Lock()

def add_items(n: int):
    global _local_item_count
    with _local_lock:
        _local_item_count += n

def get_and_reset_local_count() -> int:
    global _local_item_count
    with _local_lock:
        count = _local_item_count
        _local_item_count = 0
        return count

# 마스터 측 합산 카운터
_master_total_items = 0
_master_lock = threading.Lock()


# ── 워커 → 마스터 리포팅 훅 ──
@events.report_to_master.add_listener
def on_report_to_master(client_id, data):
    """워커가 3초마다 마스터에 보고할 때 제품 처리 수도 같이 보냄"""
    data["item_count"] = get_and_reset_local_count()


@events.worker_report.add_listener
def on_worker_report(client_id, data):
    """마스터가 워커 리포트를 받을 때 합산"""
    global _master_total_items
    with _master_lock:
        _master_total_items += data.get("item_count", 0)


# ── User 클래스 ──
class SingleApiUser(HttpUser):
    wait_time = between(0.1, 0.5)

    @task
    def recognize_single(self):
        batch_size = random.randint(1, 20)
        items = random.sample(FIXTURES, min(batch_size, len(FIXTURES)))
        for item in items:
            self.client.get(
                "/recognize",
                json={"raw_product_name": item},
                name="/recognize [단일]"
            )
            add_items(1)


class BatchSequentialUser(HttpUser):
    wait_time = between(0.1, 0.5)

    @task
    def recognize_batch_seq(self):
        batch_size = random.randint(1, 20)
        items = random.sample(FIXTURES, min(batch_size, len(FIXTURES)))
        self.client.post(
            "/recognize/batch/sequential",
            json={"raw_product_names": items},
            name="/batch/sequential [배치]"
        )
        add_items(len(items))


class BatchParallelUser(HttpUser):
    wait_time = between(0.1, 0.5)

    @task
    def recognize_batch_parallel(self):
        batch_size = random.randint(1, 20)
        items = random.sample(FIXTURES, min(batch_size, len(FIXTURES)))
        self.client.post(
            "/recognize/batch/parallel",
            json={"raw_product_names": items},
            name="/batch/parallel [병렬]"
        )
        add_items(len(items))


# ── 이벤트 훅 ──
@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    global _master_total_items
    _master_total_items = 0
    print(f"\n{'='*60}")
    print(f"RE:FRIDGE Batch API 벤치마크 시작")
    print(f"대상 호스트: {environment.host}")
    print(f"픽스처 수:   {len(FIXTURES):,}개")
    print(f"{'='*60}\n")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    stats = environment.stats.total
    p50 = stats.get_response_time_percentile(0.50) or 0
    p95 = stats.get_response_time_percentile(0.95) or 0
    p99 = stats.get_response_time_percentile(0.99) or 0

    # 마스터면 합산값, 단일 프로세스면 로컬값
    if isinstance(environment.runner, MasterRunner):
        total_items = _master_total_items
    else:
        total_items = get_and_reset_local_count() + _master_total_items

    elapsed_sec = (stats.last_request_timestamp - stats.start_time) if stats.start_time else 1
    items_per_sec = total_items / max(elapsed_sec, 1)

    print(f"\n{'='*60}")
    print(f"벤치마크 결과")
    print(f"{'-'*60}")
    print(f"HTTP 요청 수:     {stats.num_requests:,}")
    print(f"실패 수:          {stats.num_failures:,}")
    print(f"총 제품 처리 수:  {total_items:,}")
    print(f"초당 제품 처리:   {items_per_sec:.1f} items/sec")
    print(f"{'-'*60}")
    print(f"평균 응답시간:    {stats.avg_response_time:.1f}ms")
    print(f"p50: {p50:.1f}ms  |  p95: {p95:.1f}ms  |  p99: {p99:.1f}ms")
    print(f"{'='*60}\n")