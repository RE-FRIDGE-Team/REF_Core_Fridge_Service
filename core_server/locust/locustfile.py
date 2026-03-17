"""
RE:FRIDGE Recognition Pipeline 부하 테스트

실행 예시:
  # 워밍업 (10명, 10초 램프업)
  locust --headless -u 10 -r 2 --run-time 60s

  # 평상시 (N건 고정)
  locust --headless -u 50 -r 5 --iterations 5000

  # 피크타임 (300명, 20초 램프업, 5분 유지)
  locust --headless -u 300 -r 20 --run-time 300s

  # Web UI
  locust  (브라우저에서 localhost:8089)
"""

import json
import random
import os
import time
from locust import HttpUser, task, between, constant, events
from locust.runners import MasterRunner, WorkerRunner


# ── 픽스처 로드 ────────────────────────────────────────────────────
# Docker 컨테이너 내 마운트 경로: /mnt/fixtures/benchmark_fixtures.json
# compose.yaml volumes 에서 src/jmh/resources → /mnt/fixtures 마운트
FIXTURE_PATH = os.environ.get(
    "FIXTURE_PATH",
    "/mnt/fixtures/benchmark_fixtures.json"
)


def load_fixtures() -> list[str]:
    """
    benchmark_fixtures.json 에서 제품명 목록 로드.
    파일이 없으면 fallback 샘플 사용 (로컬 테스트용).
    """
    try:
        with open(FIXTURE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
            items = data.get("items", [])
            print(f"[Locust] 픽스처 로드 완료: {len(items):,}개 ({FIXTURE_PATH})")
            return items
    except FileNotFoundError:
        fallback = [
            "삼양 불닭볶음면 140g",
            "풀무원 두부 300g",
            "비비고 왕교자 1.05kg",
            "오뚜기 진라면 매운맛 120g",
            "청정원 순창 고추장 500g",
        ]
        print(f"[Locust] 픽스처 파일 없음 ({FIXTURE_PATH}), fallback {len(fallback)}개 사용")
        return fallback


# 모듈 로드 시 한 번만 읽음 — 모든 유저가 공유
FIXTURES: list[str] = load_fixtures()


# ── 공통 요청 헬퍼 ────────────────────────────────────────────────

def build_payload(requester_id: str) -> dict:
    return {
        "inputText": random.choice(FIXTURES),
        "requesterId": requester_id,
    }


def validate(response, context: str = "") -> None:
    """
    응답 검증 공통 로직.
    5xx → 실패 처리.
    200 + status 필드 있으면 성공 (COMPLETED / REJECTED / NO_MATCH 모두 정상).
    """
    if response.status_code >= 500:
        response.failure(
            f"[{context}] 서버 에러 {response.status_code}: {response.text[:200]}"
        )
    elif response.status_code == 200:
        try:
            body = response.json()
            if "status" not in body:
                response.failure(f"[{context}] 응답에 status 필드 없음: {body}")
            else:
                response.success()
        except Exception as e:
            response.failure(f"[{context}] JSON 파싱 실패: {e}")
    else:
        response.failure(
            f"[{context}] 예상치 못한 응답 코드: {response.status_code}"
        )


# ── 시나리오 1: 평상시 (wait_time 있음, RPS 자연 수렴) ─────────────

class NormalUser(HttpUser):
    """
    평상시 트래픽 시뮬레이션.
    wait_time=between(1, 3): 요청 사이 1~3초 대기.

    가상 유저 1명의 RPS ≈ 1 / (응답시간 + 평균 2초) ≈ 0.49 RPS
    목표 평균 14 RPS → 약 29명 필요
    목표 피크 140 RPS → 약 286명 필요
    """
    wait_time = between(1, 3)

    # 이 클래스를 기본으로 사용 (weight 높음)
    weight = 3

    def on_start(self):
        import uuid
        self.requester_id = str(uuid.uuid4())
        self.headers = {"Content-Type": "application/json"}

    @task(10)
    def recognize_single(self):
        """단건 제품명 인식 — 핵심 태스크"""
        payload = build_payload(self.requester_id)

        with self.client.post(
            "/api/recognition",
            json=payload,
            headers=self.headers,
            name="POST /api/recognition",
            catch_response=True,
        ) as resp:
            validate(resp, payload["inputText"])

    @task(1)
    def health_check(self):
        """헬스체크 — 가끔 호출"""
        self.client.get("/actuator/health", name="GET /actuator/health")


# ── 시나리오 2: 피크타임 (wait_time 짧게 → 높은 RPS) ───────────────

class PeakUser(HttpUser):
    """
    피크타임 트래픽 시뮬레이션.
    wait_time=constant(0.2): 0.2초 대기 → 높은 RPS.

    가상 유저 1명의 RPS ≈ 1 / (0.05 + 0.2) ≈ 4 RPS
    목표 140 RPS → 약 35명 필요

    Web UI에서 User class 선택 또는
    --headless 실행 시 --user-classes PeakUser 로 지정
    """
    wait_time = constant(0.2)
    weight = 1  # Web UI에서 NormalUser:PeakUser = 3:1 비율

    def on_start(self):
        import uuid
        self.requester_id = str(uuid.uuid4())
        self.headers = {"Content-Type": "application/json"}

    @task
    def recognize_single_peak(self):
        payload = build_payload(self.requester_id)

        with self.client.post(
            "/api/recognition",
            json=payload,
            headers=self.headers,
            name="POST /api/recognition [PEAK]",
            catch_response=True,
        ) as resp:
            validate(resp, payload["inputText"])


# ── 이벤트 훅 ────────────────────────────────────────────────────

@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    print("\n" + "=" * 60)
    print("RE:FRIDGE 부하 테스트 시작")
    print(f"대상 호스트: {environment.host}")
    print(f"픽스처 수:   {len(FIXTURES):,}개")
    print("=" * 60 + "\n")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    stats = environment.stats.total
    p95 = stats.get_response_time_percentile(0.95) or 0
    p99 = stats.get_response_time_percentile(0.99) or 0

    print("\n" + "=" * 60)
    print("RE:FRIDGE 부하 테스트 결과")
    print("-" * 60)
    print(f"총 요청 수:       {stats.num_requests:,}")
    print(f"실패 수:          {stats.num_failures:,} "
          f"({stats.num_failures / max(stats.num_requests, 1) * 100:.1f}%)")
    print(f"평균 응답시간:    {stats.avg_response_time:.1f}ms")
    print(f"p50 응답시간:     {stats.get_response_time_percentile(0.50) or 0:.1f}ms")
    print(f"p95 응답시간:     {p95:.1f}ms")
    print(f"p99 응답시간:     {p99:.1f}ms")
    print(f"최대 RPS:         {stats.max_rps:.1f}")

    # ── 목표 달성 여부 자동 판정 ──
    TARGET_P95_MS  = 300   # UX 목표: p95 < 300ms
    TARGET_FAILURE = 1.0   # 실패율 1% 미만

    failure_rate = stats.num_failures / max(stats.num_requests, 1) * 100
    p95_ok       = p95 < TARGET_P95_MS
    failure_ok   = failure_rate < TARGET_FAILURE

    print("-" * 60)
    print(f"p95 목표 (<{TARGET_P95_MS}ms):  {'✅ 달성' if p95_ok else '❌ 미달성'} ({p95:.1f}ms)")
    print(f"실패율 목표 (<{TARGET_FAILURE}%): {'✅ 달성' if failure_ok else '❌ 미달성'} ({failure_rate:.2f}%)")
    print("=" * 60 + "\n")