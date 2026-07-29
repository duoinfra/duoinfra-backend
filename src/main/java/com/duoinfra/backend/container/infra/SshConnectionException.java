package com.duoinfra.backend.container.infra;

/**
 * SSH 세션 자체를 맺지 못한 경우 전용 예외. Docker 명령 실행 실패({@link ContainerProvisionException})와
 * 구분해, 실물 자원이 생성/삭제되지 않았음이 확실한 재시도 가능 케이스임을 상위 계층이 판단할 수 있게 한다.
 */
public class SshConnectionException extends ContainerProvisionException {
    public SshConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
