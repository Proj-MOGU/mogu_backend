# MOGU Backend
![CI](https://github.com/Proj-MOGU/mogu_backend/actions/workflows/ci.yml/badge.svg)

1인 가구의 근거리 공동구매·배달·구독 비용 분담 서비스 MOGU의 백엔드 저장소.

## 로컬 실행 방법

요구사항: Java 21, Docker

```bash
docker compose up -d                              # PostgreSQL 18.6 (mogu/mogu)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun    # http://localhost:8080/actuator/health
```

테스트는 Testcontainers로 PostgreSQL을 띄우므로 Docker가 실행 중이어야 한다.

```bash
./gradlew build
```

### API 문서

local 프로파일 실행 시 http://localhost:8080/swagger-ui.html 에서 API 문서를 확인한다.
문서는 local, dev에서만 활성화되며 prod에서는 비활성화된다.
`openapi/openapi.json`은 테스트가 갱신한다. API를 바꾸면 Docker가 실행 중인 환경에서
`./gradlew test`를 실행하고 생성된 스펙 파일도 함께 커밋한다. 스펙은 손으로 수정하지 않는다.
