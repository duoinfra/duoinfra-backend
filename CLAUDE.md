# duoinfra-backend

Spring Boot 3.5 / Java 17 / MySQL 기반 백엔드 서버.

## 기술 스택

- 빌드: Gradle
- ORM: Spring Data JPA
- API 문서: springdoc-openapi → `/swagger-ui/index.html`
- 커버리지: JaCoCo (70% 미달 시 빌드 실패)
- CI/CD: GitHub Actions → EC2 SSH → systemctl restart
- 알림: Discord 웹훅 (배포 성공/실패)

## 자주 쓰는 명령어

```
./gradlew build                  # 빌드
./gradlew test                   # 테스트
./gradlew check                  # 테스트 + 커버리지 검증 (CI와 동일)
./gradlew bootRun                # 로컬 실행
./gradlew jacocoTestReport       # 커버리지 리포트 (build/reports/jacoco/)
```

## 패키지 구조

레이어 단위로 패키지를 구성하고, 각 레이어 안에서 도메인별로 나눈다.
의존 방향: Presentation → Application → Domain ← Infra

```
com.duoinfra.backend.{domain}/
  presentation/               # Presentation Layer
    - {Domain}Controller.java   # REST Controller (요청/응답 처리)

  application/                # Application Layer
    - {Domain}Service.java      # 유스케이스, 비즈니스 흐름 조율

  domain/                     # Domain Layer (외부 의존 없음)
    - {Domain}.java             # Entity
    - {Domain}Repository.java   # Repository 인터페이스

  infra/                      # Infra Layer
    - {Domain}RepositoryImpl.java  # JPA Repository 구현체
    - {Domain}JpaRepository.java   # Spring Data JPA 인터페이스
```

## 개발 워크플로우

새 기능 개발 시 아래 순서를 따른다.

1. 노션에서 태스크 생성 (제목만 작성, 상세 내용은 GitHub 이슈에 기재)
2. GitHub 이슈 생성 — 배경, 논의 포인트, 작업 범위 등 상세 내용은 **이슈 본문**에 작성
3. 노션 태스크 본문에 GitHub 이슈 URL을 링크로 삽입 (미리보기 형태)
4. 이슈 번호로 브랜치 생성: `feature/#{이슈번호}-{태스크제목}` (예: `feature/#123-create-user`)
   - 기본적으로 API 하나당 이슈/브랜치 하나
   - 기능 하나에 API가 여러 개면 브랜치를 이어서 생성 (`feature/#1` → `feature/#2` …)
5. 브랜치에서 개발
   - 테스트 코드 작성 필수
   - Swagger 문서 작성 필수
6. PR 생성 (본문에 `Closes #N` 포함, 최소 1인 코드 리뷰 및 승인 필수)
7. merge 후 CI/CD 자동 배포

## 코드 컨벤션

- 커밋 메시지: Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:` 등)
- API 응답/에러 포맷 통일
- 모든 PR은 최소 1인 이상 코드 리뷰 필수
- 테스트 커버리지 70% 이상 유지 (CI 게이트로 강제)

### 레이어 간 데이터 전달

레이어 경계를 넘을 때는 반드시 DTO를 사용한다. Entity를 레이어 밖으로 노출하지 않는다.

```
Presentation  ↔  Application : RequestDto / ResponseDto
Application   ↔  Domain      : 도메인 객체 또는 도메인 전용 DTO
Application   ↔  Infra       : 도메인 객체 또는 Infra 전용 DTO
```

- Controller는 Entity를 직접 반환하지 않는다.
- Service는 HttpServletRequest 등 웹 계층 객체를 받지 않는다.

### 의존 방향

```
Presentation → Application → Domain ← Infra
```

- 상위 레이어가 하위 레이어를 참조하는 방향만 허용한다.
- Domain은 Presentation, Application, Infra를 import하지 않는다.
- Infra는 Presentation, Application을 import하지 않는다.

### 쿼리 작성

- 단순 CRUD: Spring Data JPA (JpaRepository 메서드 또는 메서드 쿼리)
- 복잡한 조건 조회: QueryDSL
- 네이티브 쿼리(`@Query(nativeQuery = true)`) 사용 금지

### 환경변수 / 시크릿 관리

- DB 접속 정보, API 키, 토큰 등 민감한 값을 코드에 하드코딩하지 않는다.
- 모든 환경별 설정은 `application-{profile}.yml`로 분리하고, 민감한 값은 환경변수로 주입한다.
- `application-local.yml`은 `.gitignore`에 포함되어 있으므로 절대 커밋하지 않는다.
- CI/CD에서 필요한 시크릿은 GitHub Actions Secrets으로 관리한다.

## 로컬 환경 설정

`application.yml`에 DB 정보가 필요하다. 로컬에서는 `application-local.yml`을 별도 생성해 사용하며, 실제 접속 정보는 팀 내부에서 공유한다. `.gitignore`에 포함되어 있으므로 커밋하지 않는다.
