# containers 테이블 스키마 수동 반영 (#46 상태 머신)

> `ddl-auto: update`는 새 컬럼 추가는 처리하지만, 기존 컬럼의 NOT NULL 제약이나 MySQL 네이티브 ENUM의
> 허용 값 목록은 바꾸지 않는다. #46(생성/삭제 상태 머신)에서 `Container` 엔티티가 바뀌었으므로,
> 이 변경이 반영되기 전의 `containers` 테이블이 있는 모든 환경(로컬, EC2 등)에서 아래 DDL을 한 번 수동 실행해야 한다.
> 실행하지 않으면 `POST /api/servers` 호출 시 "Column 'container_id' cannot be null" 류의 DB 에러가 발생한다.

## 무엇이 바뀌었나

- `Container`는 이제 Docker 프로비저닝 전에 `CREATING` 상태로 먼저 INSERT된다. 이 시점엔 `container_id`, `host`,
  `ssh_port`, `ssh_username`, `ssh_password`가 아직 없다 → 해당 컬럼들은 nullable이어야 한다.
- `status` 컬럼은 기존 `enum('DELETED','RUNNING','STOPPED')`에서 `CREATING`, `CREATE_FAILED`, `DELETING`,
  `DELETE_FAILED`가 추가되고 `STOPPED`는 제거됐다. MySQL 네이티브 ENUM은 허용 값이 정해져 있어, DB의 enum 정의도
  같이 넓혀야 한다.

## 실행할 DDL

```sql
ALTER TABLE containers
  MODIFY COLUMN container_id VARCHAR(255) NULL,
  MODIFY COLUMN host VARCHAR(255) NULL,
  MODIFY COLUMN ssh_port INT NULL,
  MODIFY COLUMN ssh_username VARCHAR(255) NULL,
  MODIFY COLUMN ssh_password VARCHAR(255) NULL,
  MODIFY COLUMN status ENUM('CREATING','RUNNING','CREATE_FAILED','DELETING','DELETED','DELETE_FAILED') NOT NULL;
```

기존 row는 전부 `RUNNING`/`DELETED` 등 그대로 유지되는 값이라 데이터 손실 없이 안전하게 적용된다.

## 적용 대상

- **로컬 개발 DB**: 이미 이 브랜치 작업 중 위 DDL로 반영함(2026-07-29).
- **EC2 프로덕션 DB**: 이 브랜치가 merge & 배포되기 **전에** 담당자가 SSH로 직접 실행해야 한다
  (이 저장소는 자동 마이그레이션 도구가 없고, `docs/mysql-migration-plan.md`와 동일하게 수동 SSH 실행 방식을 따른다).
  배포 전에 실행하지 않으면 배포 직후 서버 생성 API가 전부 실패한다.
- **다른 팀원의 로컬 DB**: 이 브랜치를 pull한 뒤 위 DDL을 한 번 실행해야 한다.

## 알아두면 좋은 점 (근본 원인)

Hibernate 6 + MySQL 조합에서 `@Enumerated(EnumType.STRING)`은 VARCHAR가 아니라 네이티브 MySQL `ENUM` 컬럼으로
생성된다. `ddl-auto: update`는 "컬럼이 존재하는지"만 확인하고 기존 컬럼의 허용 값 목록이나 nullable 여부를 갱신하지
않으므로, **앞으로도 상태 enum에 값을 추가/제거할 때마다 이런 수동 ALTER가 반복적으로 필요하다.** 이 반복을
완전히 없애려면 Flyway 등 마이그레이션 도구 도입이 필요한데, 이는 이 저장소 전체의 스키마 관리 방식을 바꾸는
별도 논의가 필요해 이 문서/이슈 범위에서는 다루지 않는다.
