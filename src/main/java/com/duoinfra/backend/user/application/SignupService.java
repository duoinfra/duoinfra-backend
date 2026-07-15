package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SignupService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Set<String> tempAdminEmails;

    // TODO(임시 조치): DB를 직접 수정하지 않고도 관리자 권한으로 테스트할 수 있도록,
    // admin.temp-emails 프로퍼티에 등록된 이메일로 가입하면 자동으로 ADMIN 권한을 부여한다.
    // 정식 관리자 승격 API(또는 초대 절차)가 만들어지면 이 로직과 admin.temp-emails 프로퍼티를 제거할 것.
    public SignupService(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${admin.temp-emails:}") String tempAdminEmailsCsv) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tempAdminEmails = Arrays.stream(tempAdminEmailsCsv.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toSet());
    }

    @Transactional
    public SignupResult signup(SignupCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new DuplicateEmailException(command.email());
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = new User(command.email(), encodedPassword, command.nickname(), command.termsAgreed());
        if (tempAdminEmails.contains(command.email())) {
            user.promoteToAdmin();
        }
        userRepository.save(user);

        return new SignupResult(user.getId(), user.getEmail(), user.getNickname());
    }
}
