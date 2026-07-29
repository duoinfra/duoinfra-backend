# Local Docker 실행

실제 라즈베리파이 없이 로컬 Docker 데몬에서 컨테이너를 생성하려면 다음처럼 실행한다.

```bash
docker compose up -d mysql redis
SPRING_PROFILES_ACTIVE=local-docker ./gradlew bootRun
```

`local-docker` 프로파일은 SSH를 사용하지 않고 `LocalDockerClient`를 선택한다. 생성되는 컨테이너에는 `duoinfra.managed=local-docker` 라벨이 붙는다.

테스트가 끝난 뒤 생성된 컨테이너를 확인한다.

```bash
docker ps --filter label=duoinfra.managed=local-docker
```

개별 컨테이너를 삭제하고, 운영 컨테이너에는 이 라벨을 사용하지 않는다.
