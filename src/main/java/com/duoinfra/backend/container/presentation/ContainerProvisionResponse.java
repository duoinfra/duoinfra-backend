package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record ContainerProvisionResponse(
        @Schema(description = "컨테이너 ID") String containerId,
        @Schema(description = "접속 호스트") String host,
        @Schema(description = "SSH 포트") int sshPort,
        @Schema(description = "SSH 유저명") String sshUsername,
        @Schema(description = "SSH 비밀번호") String sshPassword
) {
    public static ContainerProvisionResponse from(ContainerResult result) {
        return new ContainerProvisionResponse(
                result.containerId(),
                result.host(),
                result.sshPort(),
                result.sshUsername(),
                result.sshPassword()
        );
    }
}
