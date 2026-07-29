package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerDeleteFailedException;
import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.ContainerProvisionCommand;
import com.duoinfra.backend.container.application.ContainerProvisionFailedException;
import com.duoinfra.backend.container.application.ContainerResult;
import com.duoinfra.backend.container.application.ContainerService;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerNotRunningException;
import com.duoinfra.backend.container.domain.InvalidContainerStatusTransitionException;
import com.duoinfra.backend.global.config.OpenApiConfig;
import com.duoinfra.backend.global.security.AuthenticatedUser;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Server", description = "서버(컨테이너) 관리 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/servers")
public class ContainerController {

    private final ContainerService containerService;

    public ContainerController(ContainerService containerService) {
        this.containerService = containerService;
    }

    @Operation(summary = "서버 발급", description = "물리 서버에 Ubuntu 컨테이너를 발급하고 SSH 접속 정보를 반환합니다. " +
            "중간 실패 시 502를 반환하며, GET /api/servers/{id}로 실패 상태(CREATE_FAILED)를 확인할 수 있습니다.")
    @PostMapping
    public ResponseEntity<ContainerDetailResponse> provision(@Valid @RequestBody ContainerProvisionRequest request,
                                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        ContainerProvisionCommand command = new ContainerProvisionCommand(request.cpu(), request.memory());
        ContainerResult result = containerService.provision(command, principal.userId());
        return ResponseEntity.ok(ContainerDetailResponse.from(result, principal.role()));
    }

    @Operation(summary = "서버 목록 조회", description = "USER는 본인 소유 서버만, ADMIN은 전체 사용자의 서버를 조회합니다. 삭제된(DELETED) 서버는 목록에서 제외됩니다.")
    @GetMapping
    public ResponseEntity<List<ContainerSummaryResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        List<ContainerSummaryResponse> response = containerService.list(principal.userId(), principal.role()).stream()
                .map(summary -> ContainerSummaryResponse.from(summary, principal.role()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "서버 상세 조회", description = "본인 소유 서버가 아니면 ADMIN이 아닌 한 404를 반환합니다. 삭제된(DELETED) 서버도 조회할 수 있습니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ContainerDetailResponse> getDetail(@Parameter(description = "서버 ID") @PathVariable Long id,
                                                               @AuthenticationPrincipal AuthenticatedUser principal) {
        ContainerResult result = containerService.getDetail(id, principal.userId(), principal.role());
        return ResponseEntity.ok(ContainerDetailResponse.from(result, principal.role()));
    }

    @Operation(summary = "서버 삭제", description = "본인 소유 서버가 아니면 ADMIN이 아닌 한 404를 반환합니다. " +
            "이미 삭제 중이거나 삭제 완료된 서버를 다시 삭제하려 하면 409를 반환합니다. 중간 실패 시 502를 반환합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "서버 ID") @PathVariable Long id,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        containerService.delete(id, principal.userId(), principal.role());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "서버 메트릭 조회", description = "본인 소유 서버가 아니면 ADMIN이 아닌 한 404를 반환합니다. RUNNING 상태가 아니면 409를 반환합니다.")
    @GetMapping("/{id}/metrics")
    public ResponseEntity<ContainerMetricsResponse> getMetrics(@Parameter(description = "서버 ID") @PathVariable Long id,
                                                                 @AuthenticationPrincipal AuthenticatedUser principal) {
        ContainerMetrics metrics = containerService.getMetrics(id, principal.userId(), principal.role());
        return ResponseEntity.ok(ContainerMetricsResponse.from(metrics));
    }

    @ExceptionHandler(ContainerNotFoundException.class)
    public ResponseEntity<String> handleContainerNotFound(ContainerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(ContainerProvisionFailedException.class)
    public ResponseEntity<String> handleProvisionFailed(ContainerProvisionFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                "서버 생성에 실패했습니다. id=" + e.getContainerId() + ". GET /api/servers/" + e.getContainerId() + " 으로 상세 상태를 확인하세요.");
    }

    @ExceptionHandler(ContainerDeleteFailedException.class)
    public ResponseEntity<String> handleDeleteFailed(ContainerDeleteFailedException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                "서버 삭제에 실패했습니다. id=" + e.getContainerId() + ". GET /api/servers/" + e.getContainerId() + " 으로 상세 상태를 확인하세요.");
    }

    @ExceptionHandler(InvalidContainerStatusTransitionException.class)
    public ResponseEntity<String> handleInvalidTransition(InvalidContainerStatusTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(ContainerNotRunningException.class)
    public ResponseEntity<String> handleNotRunning(ContainerNotRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
