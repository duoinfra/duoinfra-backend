package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         JwtTokenProvider jwtTokenProvider, RefreshTokenStore refreshTokenStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(command.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 로그인할 때마다 userId 기준으로 최신 Refresh Token을 덮어쓴다.
        // 그 결과 같은 계정으로 다시 로그인하면 이전에 발급됐던 Refresh Token은
        // (JWT 자체는 아직 유효 기간이 남아있어도) Redis 대조 단계에서 더 이상 통과하지 못한다.
        refreshTokenStore.save(user.getId(), refreshToken);

        return new LoginResult(accessToken, refreshToken, "Bearer", user.getEmail(), user.getNickname());
    }
}
