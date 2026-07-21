# MySQL 마이그레이션 계획 (EC2 네이티브 → Docker Compose 컨테이너)

> 이 문서는 실행 계획이며, 아직 EC2에 적용하지 않았다. 실제 마이그레이션은 팀 리뷰 후 담당자가 SSH로 직접 수행한다.

## 배경

- 현재 프로덕션 앱은 EC2 호스트에 apt로 직접 설치된 네이티브 MySQL(`localhost:3306`)에 연결되어 있다.
- Docker Compose 전환([Dockerfile](../Dockerfile), [docker-compose.yml](../docker-compose.yml))에 따라 MySQL도 컨테이너(`mysql` 서비스)로 옮긴다.
- 데이터 유실 없이 이관하는 것이 최우선이며, 완전 무중단 대신 짧은 점검창(다운타임)을 감수하는 안전한 방식을 택한다.
- 실제 EC2 적용 전, 팀원과 상의 후 진행한다.

## 0단계 — 사전 확인 (마이그레이션 착수 훨씬 이전에 먼저)

```bash
# 네이티브 MySQL 버전 확인 (docker-compose.yml의 mysql:8.0과 메이저 버전 호환 여부 판단)
mysql --version
mysql -u root -p -e "SELECT VERSION();"

# 데이터베이스 문자셋/콜레이션 확인 (dump/restore 시 깨짐 방지)
mysql -u root -p -e "SHOW CREATE DATABASE duoinfra;"

# 테이블 목록과 각 테이블 row count 기준값 기록
mysql -u root -p duoinfra -e "SHOW TABLES;"
mysql -u root -p duoinfra -e "
  SELECT table_name, table_rows
  FROM information_schema.tables
  WHERE table_schema='duoinfra';"
```

- 네이티브가 5.7이면 `sql_mode`, 인증 플러그인(`mysql_native_password` vs `caching_sha2_password`) 차이가 있을 수 있으므로, `docker-compose.yml`의 mysql 이미지 태그를 네이티브와 같은 메이저 버전으로 맞추는 것을 우선 검토한다.
- `information_schema.tables.table_rows`는 InnoDB에서 추정치이므로, 최종 검증(4단계)에서는 `COUNT(*)`로 재확인한다.

## 1단계 — 사전 백업 (컷오버 며칠 전, 서비스 무중단)

```bash
mysqldump -u root -p \
  --single-transaction \
  --routines --triggers \
  --default-character-set=utf8mb4 \
  duoinfra > ~/backups/duoinfra_pre_$(date +%Y%m%d_%H%M).sql

ls -lh ~/backups/duoinfra_pre_*.sql
grep -c "^CREATE TABLE" ~/backups/duoinfra_pre_*.sql

# EC2 외부(로컬 등)로도 복사 — 인스턴스 자체 장애 대비
scp ec2-host:~/backups/duoinfra_pre_*.sql ~/Downloads/
```

`--single-transaction`으로 InnoDB 기준 락 없이 일관된 스냅샷을 뜬다. 이 단계는 리허설 겸 안전망으로, 컷오버와 무관하게 미리 해봐도 된다.

## 2단계 — 컷오버: 쓰기 차단 + 최종 덤프

```bash
# 트래픽 적은 시간대에, 추가 쓰기 차단
sudo systemctl stop duoinfra-backend
sudo systemctl status duoinfra-backend   # inactive 확인

# 정지 직후 뜨는, 이관의 "진짜" 소스가 되는 최종 덤프
mysqldump -u root -p \
  --single-transaction \
  --routines --triggers \
  --default-character-set=utf8mb4 \
  duoinfra > ~/backups/duoinfra_final_$(date +%Y%m%d_%H%M).sql

ls -lh ~/backups/duoinfra_final_*.sql
```

## 3단계 — 새 컨테이너 기동 + restore

```bash
cd ~/duoinfra-backend
docker compose up -d mysql
watch docker compose ps   # healthy 될 때까지 대기 (Ctrl+C로 종료)

docker exec -i duoinfra-mysql mysql -u root -p"$DB_PASSWORD" duoinfra < ~/backups/duoinfra_final_YYYYMMDD_HHMM.sql
```

## 4단계 — 검증 (앱은 아직 새 DB에 연결하지 않은 상태)

```bash
# (a) 테이블 목록이 원본과 동일한지
docker exec -it duoinfra-mysql mysql -u root -p"$DB_PASSWORD" duoinfra -e "SHOW TABLES;"

# (b) 테이블별 실제 row count 비교 (실제 테이블명으로 교체, 전체 테이블 확인)
docker exec -it duoinfra-mysql mysql -u root -p"$DB_PASSWORD" duoinfra -e "
  SELECT COUNT(*) FROM <table_name>;"

# (c) 체크섬 비교 — 원본에서도 동일 명령 실행 후 값 대조
docker exec -it duoinfra-mysql mysql -u root -p"$DB_PASSWORD" duoinfra -e "CHECKSUM TABLE <table1>, <table2>;"

# (d) AUTO_INCREMENT 값이 끊기지 않았는지 (새 insert 시 PK 충돌 방지)
docker exec -it duoinfra-mysql mysql -u root -p"$DB_PASSWORD" duoinfra -e "
  SELECT table_name, auto_increment
  FROM information_schema.tables WHERE table_schema='duoinfra';"

# (e) 문자셋 깨짐 확인 — non-ASCII 값이 있는 컬럼 직접 조회
docker exec -it duoinfra-mysql mysql -u root -p"$DB_PASSWORD" duoinfra -e "SELECT * FROM <table_name> LIMIT 5;"
```

**Go/No-Go 기준**: (a)~(e) 모두 원본과 일치해야 다음 단계로 진행한다. 하나라도 불일치하면 5단계로 넘어가지 않고, 원본 MySQL은 그대로 둔 채 원인을 먼저 파악한다.

## 5단계 — 앱을 새 DB에 연결, 스모크 테스트

```bash
docker compose up -d --build app
docker compose logs -f app   # 정상 기동 로그, 에러 여부 확인

curl localhost:8080/actuator/health
```

실제 데이터가 걸린 API(로그인, 목록 조회 등)를 몇 개 직접 호출해 이관 전과 응답값이 같은지 확인한다.

## 6단계 — 확정 및 롤백 안전망

- 검증 통과 시: `sudo systemctl disable duoinfra-backend` (이미 stop된 상태), `deploy.yml`을 Docker Compose 기반 스크립트로 교체.
- 원본 데이터는 삭제하지 않고 최소 몇 주 보존: `/var/lib/mysql`, `~/backups/duoinfra_final_*.sql`, `~/backups/duoinfra_pre_*.sql` 전부 유지.
- 문제 발생 시 즉시 롤백: `docker compose stop app` → `sudo systemctl start duoinfra-backend` (원본 MySQL은 그대로이므로 즉시 정상 서비스 재개 가능).

## 범위에서 제외된 것

- `deploy.yml` 자동화 변경은 이 문서/브랜치에 포함하지 않는다. 마이그레이션이 실제로 완료되어 검증될 때까지 서버는 기존 systemd 방식으로 운영한다.
- 이 마이그레이션 자체(백업/컷오버/restore/검증)는 워크플로우에 자동화하지 않고, 담당자가 SSH로 각 단계 결과를 직접 확인하며 수동 진행한다.
