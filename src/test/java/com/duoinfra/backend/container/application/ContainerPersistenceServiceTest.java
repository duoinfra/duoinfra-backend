package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import com.duoinfra.backend.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContainerPersistenceServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private UserRepository userRepository;

    private ContainerPersistenceService containerPersistenceService;

    @BeforeEach
    void setUp() {
        containerPersistenceService = new ContainerPersistenceService(containerRepository, userRepository);
    }

    private User userWithId(Long id) {
        User user = new User(id + "@test.com", "encoded-pw", "user" + id, true);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Container containerOwnedBy(Long containerId, User owner) {
        Container container = new Container("docker-" + containerId, "220.117.221.158", 10000,
                "root", "password1234", 1, 512, owner);
        ReflectionTestUtils.setField(container, "id", containerId);
        return container;
    }

    @Test
    @DisplayName("존재하는 사용자를 조회하면 해당 사용자를 반환한다")
    void findOwner_exists_returnsUser() {
        User owner = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        User result = containerPersistenceService.findOwner(1L);

        assertThat(result).isEqualTo(owner);
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 조회하면 예외가 발생한다")
    void findOwner_notExists_throws() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> containerPersistenceService.findOwner(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("컨테이너를 저장하면 리포지토리에 위임한다")
    void saveContainer_delegatesToRepository() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerRepository.save(container)).willReturn(container);

        Container result = containerPersistenceService.saveContainer(container);

        assertThat(result).isEqualTo(container);
        verify(containerRepository).save(container);
    }

    @Test
    @DisplayName("본인 소유 컨테이너는 접근할 수 있다")
    void getAccessibleContainer_ownedByRequester_returnsContainer() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER);

        assertThat(result).isEqualTo(container);
    }

    @Test
    @DisplayName("ADMIN은 다른 사용자 소유 컨테이너도 접근할 수 있다")
    void getAccessibleContainer_notOwnedButAdmin_returnsContainer() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.getAccessibleContainer(100L, 99L, Role.ADMIN);

        assertThat(result).isEqualTo(container);
    }

    @Test
    @DisplayName("USER가 다른 사용자 소유 컨테이너에 접근하면 예외가 발생한다")
    void getAccessibleContainer_notOwnedAndNotAdmin_throwsNotFound() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        assertThatThrownBy(() -> containerPersistenceService.getAccessibleContainer(100L, 2L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 컨테이너에 접근하면 예외가 발생한다")
    void getAccessibleContainer_notExists_throwsNotFound() {
        given(containerRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
    }

    @Test
    @DisplayName("컨테이너를 삭제하면 리포지토리에 위임한다")
    void deleteContainer_delegatesToRepository() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);

        containerPersistenceService.deleteContainer(container);

        verify(containerRepository).delete(container);
    }
}
