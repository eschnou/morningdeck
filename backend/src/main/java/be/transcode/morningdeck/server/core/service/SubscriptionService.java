package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.model.CreditUsageLog;
import be.transcode.morningdeck.server.core.model.Subscription;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.CreditUsageLogRepository;
import be.transcode.morningdeck.server.core.repository.SubscriptionRepository;
import be.transcode.morningdeck.server.core.repository.UserRepository;
import be.transcode.morningdeck.server.provider.emailsend.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;
    private final CreditUsageLogRepository creditUsageLogRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${application.root-domain:localhost}")
    private String domain;

    @Transactional
    public Subscription createSubscription(User user, Subscription.SubscriptionPlan plan, boolean autoRenew) {
        Subscription subscription = Subscription.builder()
                .plan(plan)
                .autoRenew(autoRenew)
                .creditsBalance(plan.getMonthlyCredits())
                .monthlyCredits(plan.getMonthlyCredits())
                .build();
        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public boolean useCredits(User user, int credits) {
        Subscription subscription = subscriptionRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));

        if (subscription.getCreditsBalance() < credits) {
            return false;
        }

        subscription.setCreditsBalance(subscription.getCreditsBalance() - credits);
        subscriptionRepository.save(subscription);

        creditUsageLogRepository.save(CreditUsageLog.builder()
                .user(user)
                .subscription(subscription)
                .creditsUsed(credits)
                .build());

        return true;
    }

    @Transactional
    public void upgradePlan(UUID subscriptionId, Subscription.SubscriptionPlan newPlan) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        subscription.setPlan(newPlan);
        subscription.setCreditsBalance(newPlan.getMonthlyCredits());
        subscription.setMonthlyCredits(newPlan.getMonthlyCredits());
        subscriptionRepository.save(subscription);
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    @Transactional
    public void checkAndRenewSubscriptions() {
        Instant now = Instant.now();

        subscriptionRepository.findByAutoRenewTrueAndNextRenewalDateBefore(now)
                .forEach(subscription -> {
                    subscription.setCreditsBalance(subscription.getMonthlyCredits());
                    subscription.setNextRenewalDate(subscription.getNextRenewalDate().atZone(ZoneOffset.UTC).plusMonths(1).toInstant());
                    subscriptionRepository.save(subscription);
                });
    }

    /**
     * Check if user has any credits (> 0).
     */
    public boolean hasCredits(UUID userId) {
        return subscriptionRepository.findCreditsBalanceByUserId(userId)
                .map(balance -> balance > 0)
                .orElse(false);
    }

    /**
     * Get credit balance for user.
     */
    public int getCreditsBalance(UUID userId) {
        return subscriptionRepository.findCreditsBalanceByUserId(userId).orElse(0);
    }

    /**
     * Get set of user IDs that have credits > 0.
     * Used by schedulers for efficient batch filtering.
     */
    public Set<UUID> getUserIdsWithCredits() {
        return subscriptionRepository.findUserIdsWithCredits();
    }

    /**
     * Deduct credits from user's subscription.
     * Triggers no-credits notification when balance hits zero.
     */
    @Transactional
    public boolean useCredits(UUID userId, int credits) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        boolean success = useCredits(user, credits);

        if (success) {
            Subscription subscription = subscriptionRepository.findByUser(user).orElse(null);
            if (subscription != null && subscription.getCreditsBalance() == 0) {
                sendNoCreditsNotification(user);
            }
        }

        return success;
    }

    private void sendNoCreditsNotification(User user) {
        try {
            emailService.sendNoCreditsEmail(user.getEmail(), user.getName(), domain);
            log.info("Sent no-credits notification to user {}", user.getId());
        } catch (Exception e) {
            log.error("Failed to send no-credits notification to user {}: {}", user.getId(), e.getMessage());
        }
    }
}
