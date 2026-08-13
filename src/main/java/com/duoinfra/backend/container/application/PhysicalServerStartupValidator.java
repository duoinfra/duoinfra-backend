package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.PhysicalServerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PhysicalServerStartupValidator implements ApplicationRunner {

    private final PhysicalServerRepository physicalServerRepository;
    private final Long physicalServerId;

    public PhysicalServerStartupValidator(PhysicalServerRepository physicalServerRepository,
                                          @Value("${physical-server.id}") Long physicalServerId) {
        this.physicalServerRepository = physicalServerRepository;
        this.physicalServerId = physicalServerId;
    }

    @Override
    public void run(ApplicationArguments args) {
        physicalServerRepository.findById(physicalServerId)
                .orElseThrow(() -> new IllegalStateException(
                        "physical-server.id로 설정된 물리 서버(id=" + physicalServerId + ")가 DB에 존재하지 않습니다. " +
                                "PhysicalServer row를 먼저 등록해주세요."
                ));
    }
}