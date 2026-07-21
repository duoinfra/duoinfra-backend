package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import com.duoinfra.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 전용 작업만 짧게 트랜잭션으로 감싸는 협력 객체.
 * DockerClient(SSH) 호출처럼 느린 외부 작업이 DB 트랜잭션 안에 포함되지 않도록
 * {@link ContainerService}에서 분리했다. (같은 클래스 내부 self-invocation은
 * 프록시 기반 @Transactional이 적용되지 않으므로 별도 빈으로 분리가 필요하다.)
 */
@Service
public class ContainerPersistenceService {

    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;

    public ContainerPersistenceService(ContainerRepository containerRepository, UserRepository userRepository) {
        this.containerRepository = containerRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User findOwner(Long requesterId) {
        return userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException(requesterId));
    }

    @Transactional
    public Container saveContainer(Container container) {
        return containerRepository.save(container);
    }

    @Transactional(readOnly = true)
    public Container getAccessibleContainer(Long id, Long requesterId, Role requesterRole) {
        Container container = containerRepository.findById(id)
                .orElseThrow(() -> new ContainerNotFoundException(id));

        boolean isOwner = container.getOwner().getId().equals(requesterId);
        if (requesterRole != Role.ADMIN && !isOwner) {
            // 다른 사용자의 서버가 존재한다는 사실 자체를 노출하지 않기 위해 403이 아닌 404로 응답한다.
            throw new ContainerNotFoundException(id);
        }
        return container;
    }

    @Transactional
    public void deleteContainer(Container container) {
        containerRepository.delete(container);
    }
}
