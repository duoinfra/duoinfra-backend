# duoinfra-backend
동시성·성능·인프라 구축 학습 및 실험

## 로컬 개발 환경

세 가지 방식 중 편한 것을 골라 쓰면 된다. 셋 다 동시에 지원되며, 우열은 없다.

### 방식 A. 기존 방식 — 로컬에 MySQL 직접 설치

1. 로컬에 MySQL을 설치하고 `duoinfra` 데이터베이스를 만든다.
2. `src/main/resources/application-local.yml`을 생성하고 접속 정보를 채운다 (`.gitignore`에 포함, 팀 내부에서 공유받는다).
3. 실행: `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`

### 방식 B. Docker Compose — app + MySQL + Redis 전부 컨테이너

1. `.env.example`을 `.env`로 복사하고 값을 채운다.
2. JAR 빌드: `./gradlew bootJar` — Dockerfile이 `build/libs/*.jar`를 그대로 복사하는 방식이라 이미지 빌드 전 매번 필요하다.
3. 실행: `docker compose up --build`
4. 접속: 앱은 `localhost:8080`, MySQL은 `localhost:3307`(컴포즈 네트워크 안에서는 `mysql:3306`), Redis는 `localhost:6379`.

### 방식 C. 하이브리드 — MySQL/Redis만 컨테이너, 앱은 로컬 bootRun

1. `.env` 준비 후 인프라만 실행: `docker compose up mysql redis`
2. `application-local.yml`의 datasource url 포트를 컴포즈가 노출한 포트(`3307`)에 맞춘다. 이 파일은 개인별 파일이라 자유롭게 조정하면 된다.
3. 실행: `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`
4. JAR 빌드나 이미지 빌드 없이 바로 뜨거운 재시작/디버깅이 가능하면서, 실제 컨테이너 MySQL/Redis를 그대로 쓸 수 있다.

### 자주 쓰는 Docker Compose 명령어

| 목적 | 명령어 |
|---|---|
| 전체 기동 (이미지 빌드 포함) | `docker compose up --build` |
| 인프라(MySQL/Redis)만 기동 | `docker compose up mysql redis` |
| 백그라운드 기동 | `docker compose up -d --build` |
| 종료 | `docker compose down` |
| 종료 + 볼륨 삭제 (DB 데이터 초기화) | `docker compose down -v` |
| 로그 확인 | `docker compose logs -f app` |

Gradle 명령어는 `CLAUDE.md`를 참고.
