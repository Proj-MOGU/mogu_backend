# MOGU Backend

1인 가구의 근거리 공동구매·배달·구독 비용 분담 서비스 MOGU의 백엔드 저장소.

## 로컬 실행 방법

요구사항: Java 21, Docker

```bash
docker compose up -d                              # PostgreSQL 17 (mogu/mogu)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun    # http://localhost:8080/actuator/health
```

테스트는 Testcontainers로 PostgreSQL을 띄우므로 Docker가 실행 중이어야 한다.

```bash
./gradlew build
```
