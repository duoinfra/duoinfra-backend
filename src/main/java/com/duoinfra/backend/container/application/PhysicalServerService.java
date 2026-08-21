package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.PhysicalServer;
import com.duoinfra.backend.container.domain.PhysicalServerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhysicalServerService {

    private final PhysicalServerRepository physicalServerRepository;

    public PhysicalServerService(PhysicalServerRepository physicalServerRepository) {
        this.physicalServerRepository = physicalServerRepository;
    }

    @Transactional
    public void reserve(Long physicalServerId, int cpu, int memory) {
        // 1. 락을 걸면서 조회 (findByIdForUpdate 사용)
        PhysicalServer server = physicalServerRepository.findByIdForUpdate(physicalServerId)
                .orElseThrow(() -> new IllegalStateException("물리 서버를 찾을 수 없습니다: " + physicalServerId));
        // 2. 없으면 예외? (일단 EntityNotFoundException 같은 거 생각)
        // 3. allocate() 호출 — 여기서 자원 부족하면 PhysicalServer 안에서 이미 예외가 던져짐
        server.allocate(cpu, memory);
    }

    @Transactional
    public void release(Long physicalServerId, int cpu, int memory) {
        PhysicalServer server = physicalServerRepository.findByIdForUpdate(physicalServerId)
                .orElseThrow(() -> new IllegalStateException("물리 서버를 찾을 수 없습니다: " + physicalServerId));
        server.release(cpu, memory);
    }
}