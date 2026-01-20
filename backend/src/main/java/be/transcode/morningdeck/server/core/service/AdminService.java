package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.config.AppConfig;
import be.transcode.morningdeck.server.core.dto.AdminAdjustCreditsDTO.CreditAdjustmentMode;
import be.transcode.morningdeck.server.core.dto.AdminUserDetailDTO;
import be.transcode.morningdeck.server.core.dto.AdminUserListItemDTO;
import be.transcode.morningdeck.server.core.dto.SubscriptionStatusDTO;
import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.model.Subscription;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.SubscriptionRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import be.transcode.morningdeck.server.provider.analytics.AnalyticsService;
import be.transcode.morningdeck.server.provider.emailsend.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AnalyticsService analyticsService;
    private final EmailService emailService;
    private final AppConfig appConfig;

    @Transactional(readOnly = true)
    public Page<AdminUserListItemDTO> listUsers(String search, Boolean enabled, Pageable pageable) {
        Page<User> users;

        if (search != null && !search.isBlank() && enabled != null) {
            users = userRepository.findBySearchAndEnabled(search, enabled, pageable);
        } else if (search != null && !search.isBlank()) {
            users = userRepository.findBySearch(search, pageable);
        } else if (enabled != null) {
            users = userRepository.findByEnabled(enabled, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(this::mapToListItemDTO);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailDTO getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return mapToDetailDTO(user);
    }

    @Transactional
    public AdminUserDetailDTO updateUserEnabled(UUID adminId, UUID userId, boolean enabled) {
        if (adminId.equals(userId)) {
            throw new BadRequestException("Cannot change your own enabled status");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setEnabled(enabled);
        user = userRepository.save(user);

        String eventName = enabled ? "ADMIN_ENABLE_USER" : "ADMIN_DISABLE_USER";
        log.warn("Admin {} {} user {}", adminId, enabled ? "enabled" : "disabled", userId);
        analyticsService.logEvent(eventName, adminId.toString(), Map.of(
                "targetUserId", userId.toString(),
                "enabled", String.valueOf(enabled)
        ));

        return mapToDetailDTO(user);
    }

    @Transactional
    public void resetPassword(UUID adminId, UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Send notification email
        emailService.sendPasswordResetByAdminEmail(user.getEmail(), user.getName(), appConfig.getRootDomain());

        log.warn("Admin {} reset password for user {}", adminId, userId);
        analyticsService.logEvent("ADMIN_RESET_PASSWORD", adminId.toString(), Map.of(
                "targetUserId", userId.toString()
        ));
    }

    @Transactional
    public void updateEmail(UUID adminId, UUID userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String normalizedEmail = newEmail.toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail) && !user.getEmail().equals(normalizedEmail)) {
            throw new BadRequestException("Email already exists");
        }

        String oldEmail = user.getEmail();
        user.setEmail(normalizedEmail);
        userRepository.save(user);

        // Send notification to both old and new email addresses
        emailService.sendEmailChangedByAdminEmail(oldEmail, user.getName(), normalizedEmail, appConfig.getRootDomain());
        emailService.sendEmailChangedByAdminEmail(normalizedEmail, user.getName(), normalizedEmail, appConfig.getRootDomain());

        log.warn("Admin {} changed email for user {} from {} to {}", adminId, userId, oldEmail, normalizedEmail);
        analyticsService.logEvent("ADMIN_CHANGE_EMAIL", adminId.toString(), Map.of(
                "targetUserId", userId.toString(),
                "oldEmail", oldEmail,
                "newEmail", normalizedEmail
        ));
    }

    @Transactional
    public void updateSubscription(UUID adminId, UUID userId, Subscription.SubscriptionPlan plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Subscription subscription = user.getSubscription();
        if (subscription == null) {
            throw new ResourceNotFoundException("Subscription not found for user: " + userId);
        }

        subscription.setPlan(plan);
        subscription.setCreditsBalance(plan.getMonthlyCredits());
        subscription.setMonthlyCredits(plan.getMonthlyCredits());
        subscriptionRepository.save(subscription);

        log.warn("Admin {} changed subscription for user {} to {}", adminId, userId, plan);
        analyticsService.logEvent("ADMIN_CHANGE_SUBSCRIPTION", adminId.toString(), Map.of(
                "targetUserId", userId.toString(),
                "newPlan", plan.name()
        ));
    }

    @Transactional
    public void adjustCredits(UUID adminId, UUID userId, int amount, CreditAdjustmentMode mode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Subscription subscription = user.getSubscription();
        if (subscription == null) {
            throw new ResourceNotFoundException("Subscription not found for user: " + userId);
        }

        int newBalance;
        if (mode == CreditAdjustmentMode.SET) {
            if (amount < 0) {
                throw new BadRequestException("Credits balance cannot be negative");
            }
            newBalance = amount;
        } else {
            newBalance = subscription.getCreditsBalance() + amount;
            if (newBalance < 0) {
                throw new BadRequestException("Credits balance cannot go negative");
            }
        }

        subscription.setCreditsBalance(newBalance);
        subscriptionRepository.save(subscription);

        log.warn("Admin {} adjusted credits for user {} ({} {}) to {}",
                adminId, userId, mode, amount, newBalance);
        analyticsService.logEvent("ADMIN_ADJUST_CREDITS", adminId.toString(), Map.of(
                "targetUserId", userId.toString(),
                "mode", mode.name(),
                "amount", String.valueOf(amount),
                "newBalance", String.valueOf(newBalance)
        ));
    }

    private AdminUserListItemDTO mapToListItemDTO(User user) {
        Subscription subscription = user.getSubscription();
        return AdminUserListItemDTO.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .subscriptionPlan(subscription != null ? subscription.getPlan().name() : null)
                .creditsBalance(subscription != null ? subscription.getCreditsBalance() : null)
                .build();
    }

    private AdminUserDetailDTO mapToDetailDTO(User user) {
        Subscription subscription = user.getSubscription();
        SubscriptionStatusDTO subscriptionDTO = null;

        if (subscription != null) {
            subscriptionDTO = SubscriptionStatusDTO.builder()
                    .id(subscription.getId())
                    .plan(subscription.getPlan().name())
                    .creditsBalance(subscription.getCreditsBalance())
                    .monthlyCredits(subscription.getMonthlyCredits())
                    .nextRenewalDate(subscription.getNextRenewalDate())
                    .autoRenew(subscription.isAutoRenew())
                    .build();
        }

        return AdminUserDetailDTO.builder()
                .id(user.getId().toString())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .language(user.getLanguage())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .subscription(subscriptionDTO)
                .build();
    }
}
