package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerProvisionCommand;
import com.duoinfra.backend.container.application.ContainerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Container", description = "컨테이너 발급 API")
@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final ContainerService containerService;

    public ContainerController(ContainerService containerService) {
        this.containerService = containerService;
    }

    @Operation(summary = "컨테이너 발급", description = "물리 서버에 Ubuntu 컨테이너를 발급하고 SSH 접속 정보를 반환합니다.")
    @PostMapping
    public ResponseEntity<ContainerProvisionResponse> provision(@Valid @RequestBody ContainerProvisionRequest request) {
        ContainerProvisionCommand command = new ContainerProvisionCommand(request.cpu(), request.memory());
        ContainerProvisionResponse response = ContainerProvisionResponse.from(containerService.provision(command));
        return ResponseEntity.ok(response);
    }
}
