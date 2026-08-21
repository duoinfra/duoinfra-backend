package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.container.domain.ContainerStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 비동기 프로비저닝(ContainerProvisioningWorker) 처리 중 release()와 markCreateFailed()가
 * 모두 실패하는 이중 실패가 발생하면, 컨테이너가 CREATING 상태로 영원히 방치될 수 있다.
 * 이 스케줄러는 그런 방치 상태를 주기적으로 탐지해 알린다.
 *
 * 현재는 CRITICAL 로그로 알리는 것까지만 담당한다.
 * 실물(Docker) 확인 후 자동으로 상태를 재조정하는 로직은 #60(Docker-DB 상태 정합성 점검 및 복구)에서 구현 예정.
 */
@Component
public class StaleContainerReconciler {

    private static final Logger log = LoggerFactory.getLogger(StaleContainerReconciler.class);
    private static final int STALE_THRESHOLD_MINUTES = 5;

    private final ContainerRepository containerRepository;

    public StaleContainerReconciler(ContainerRepository containerRepository) {
        this.containerRepository = containerRepository;
    }

    @Scheduled(fixedRate = 60000)
    public void detectStaleCreatingContainers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
        List<Container> stale = containerRepository.findAllByStatusAndCreatedAtBefore(
                ContainerStatus.CREATING, threshold);

        for (Container container : stale) {
            log.error("CRITICAL: 컨테이너(id={})가 {}분 이상 CREATING 상태로 방치되어 있습니다. 수동 확인이 필요합니다.",
                    container.getId(), STALE_THRESHOLD_MINUTES);
        }
    }
}