package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.config.AppConfig;
import be.transcode.morningdeck.server.config.EmailVerificationProperties;
import be.transcode.morningdeck.server.core.dto.VerificationResponse;
import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.model.EmailVerificationToken;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.EmailVerificationTokenRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import be.transcode.morningdeck.server.provider.analytics.AnalyticsService;
import be.transcode.morningdeck.server.provider.emailsend.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final EmailVerificationProperties properties;
    private final AppConfig appConfig;
    private final AnalyticsService analyticsService;

    public boolean isVerificationEnabled() {
        return properties.isEnabled();
    }

    @Transactional
    public void createAndSendVerification(User user) {
        // Delete any existing tokens for this user
        tokenRepository.deleteByUser(user);

        // Generate new token
        String token = generateToken();
        String tokenHash = hashToken(token);

        // Save token
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().atZone(ZoneOffset.UTC).plusHours(properties.getExpirationHours()).toInstant())
                .build();
        tokenRepository.save(verificationToken);

        // Build verification link
        String verificationLink = String.format("%s/verify-email?token=%s",
                appConfig.getRootUrl(), token);

        // Send email
        emailService.sendVerificationEmail(
                user.getEmail(),
                user.getName(),
                verificationLink,
                properties.getExpirationHours(),
                appConfig.getRootDomain()
        );

        log.info("Verification email sent to {}", maskEmail(user.getEmail()));
    }

    @Transactional
    public VerificationResponse verifyEmail(String token) {
        String tokenHash = hashToken(token);

        EmailVerificationToken verificationToken = tokenRepository.findByTokenHash(tokenHash)
                .orElse(null);

        if (verificationToken == null) {
            return VerificationResponse.builder()
                    .success(false)
                    .message("Invalid or expired verification link")
                    .build();
        }

        if (verificationToken.isExpired()) {
            tokenRepository.delete(verificationToken);
            return VerificationResponse.builder()
                    .success(false)
                    .message("Verification link has expired. Please request a new one.")
                    .build();
        }

        // Verify user
        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        // Delete token
        tokenRepository.delete(verificationToken);

        // Send welcome email now that user is verified
        emailService.sendWelcomeEmail(user.getEmail(), user.getName(), appConfig.getRootDomain());

        log.info("Email verified for user {}", user.getId());
        analyticsService.logEvent("EMAIL_VERIFIED", user.getId().toString());

        return VerificationResponse.builder()
                .success(true)
                .message("Email verified successfully. You can now login.")
                .build();
    }

    @Transactional
    public void resendVerification(String email) {
        String normalizedEmail = email.toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        // Don't reveal if email exists
        if (user == null) {
            log.warn("Resend verification requested for non-existent email: {}", maskEmail(normalizedEmail));
            return;
        }

        if (user.isEmailVerified()) {
            log.warn("Resend verification requested for already verified email: {}", maskEmail(normalizedEmail));
            return;
        }

        // Check rate limit: max 3 per hour
        int recentCount = tokenRepository.countByUserAndCreatedAtAfter(
                user, Instant.now().atZone(ZoneOffset.UTC).minusHours(1).toInstant());
        if (recentCount >= 3) {
            throw new BadRequestException("Too many verification requests. Please try again later.");
        }

        createAndSendVerification(user);
    }

    @Transactional
    public void adminVerifyEmail(UUID adminId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.isEmailVerified()) {
            throw new BadRequestException("User email is already verified");
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        // Delete any pending tokens
        tokenRepository.deleteByUser(user);

        // Send welcome email
        emailService.sendWelcomeEmail(user.getEmail(), user.getName(), appConfig.getRootDomain());

        log.warn("Admin {} manually verified email for user {}", adminId, userId);
        analyticsService.logEvent("ADMIN_VERIFY_EMAIL", adminId.toString(), Map.of(
                "targetUserId", userId.toString()
        ));
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteByExpiresAtBefore(Instant.now());
        log.debug("Cleaned up expired verification tokens");
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
