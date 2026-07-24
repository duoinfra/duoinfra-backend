package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import com.duoinfra.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public RefreshTokenService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider,
                                RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional(readOnly = true)
    public RefreshResult refresh(RefreshCommand command) {
        String refreshToken = command.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 서명/만료가 유효한 JWT라도, Redis에 저장된 "현재 유효한" Refresh Token과 일치하는지 반드시 대조한다.
        // 이 대조 단계가 있어야 재로그인으로 교체되어 이미 폐기된 옛 Refresh Token이나,
        // (userId만 담고 있는) Access Token을 Refresh Token 대신 사용하려는 시도를 함께 차단할 수 있다.
        String storedRefreshToken = refreshTokenStore.findByUserId(userId)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!storedRefreshToken.equals(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }

        // Refresh Token에는 email/role을 담아두지 않았으므로, 재발급 시점의 최신 정보를 다시 조회한다.
        // (예: 그 사이 권한이 변경되었더라도 새 Access Token에는 최신 role이 반영된다)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        return new RefreshResult(newAccessToken, "Bearer");
    }
}
