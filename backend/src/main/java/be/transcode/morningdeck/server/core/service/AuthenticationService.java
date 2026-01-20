package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.config.AppConfig;
import be.transcode.morningdeck.server.core.dto.AuthRequest;
import be.transcode.morningdeck.server.core.dto.AuthResponse;
import be.transcode.morningdeck.server.core.dto.RegisterRequest;
import be.transcode.morningdeck.server.core.dto.RegisterResponse;
import be.transcode.morningdeck.server.core.dto.UserProfileDTO;
import be.transcode.morningdeck.server.core.model.InviteCode;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.provider.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    public static final String LOGIN = "LOGIN";

    private final UserService userService;
    private final JwtService jwtService;
    private final AnalyticsService analyticsService;
    private final AuthenticationManager authenticationManager;
    private final AppConfig appConfig;
    private final EmailVerificationService emailVerificationService;
    private final InviteCodeService inviteCodeService;

    @Transactional
    public Object register(RegisterRequest request) {

        // Validate and use invite code if closed beta is enabled
        InviteCode inviteCode = null;
        if (appConfig.isClosedBeta()) {
            inviteCode = inviteCodeService.validateAndUse(request.getInviteCode());
            log.info("User {} is signing up with invite code {}", request.getUsername(), inviteCode.getCode());
        }

        boolean verificationEnabled = emailVerificationService.isVerificationEnabled();

        UserProfileDTO userDto = userService.createUser(
                request.getUsername(),
                request.getName(),
                request.getEmail(),
                request.getPassword(),
                request.getLanguage(),
                !verificationEnabled, // sendWelcomeEmail: only if verification disabled
                inviteCode
        );

        if (verificationEnabled) {
            // Get the user entity for verification
            User user = userService.getInternalUserByUsername(request.getUsername());
            emailVerificationService.createAndSendVerification(user);

            return RegisterResponse.builder()
                    .message("Registration successful. Please check your email to verify your account.")
                    .email(emailVerificationService.maskEmail(request.getEmail()))
                    .build();
        } else {
            // Verification disabled - return JWT immediately
            // Set emailVerified to true for the user
            User user = userService.getInternalUserByUsername(request.getUsername());
            user.setEmailVerified(true);
            userService.saveUser(user);

            String token = jwtService.generateToken(
                    userService.loadUserByUsername(request.getUsername())
            );

            return AuthResponse.builder()
                    .token(token)
                    .user(userDto)
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse authenticate(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        log.info("User {} is authenticated.", request.getUsername());

        var userDetails = userService.loadUserByUsername(request.getUsername());
        var userDto = userService.getUserByUsername(userDetails.getUsername());
        var token = jwtService.generateToken(userDetails);

        analyticsService.logEvent(LOGIN, userDto.getId().toString());

        return AuthResponse.builder()
                .token(token)
                .user(userDto)
                .build();
    }
}
