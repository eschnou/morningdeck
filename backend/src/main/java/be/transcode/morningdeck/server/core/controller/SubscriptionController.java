package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.core.dto.CreditUsageDTO;
import be.transcode.morningdeck.server.core.dto.SubscriptionStatusDTO;
import be.transcode.morningdeck.server.core.model.CreditUsageLog;
import be.transcode.morningdeck.server.core.model.Subscription;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.repository.CreditUsageLogRepository;
import be.transcode.morningdeck.server.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
    private final CreditUsageLogRepository creditUsageLogRepository;
    private final UserService userService;

    @GetMapping()
    public ResponseEntity<SubscriptionStatusDTO> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        Subscription subscription = user.getSubscription();

        return ResponseEntity.ok(SubscriptionStatusDTO.builder()
                .id(subscription.getId())
                .plan(subscription.getPlan().name())
                .creditsBalance(subscription.getCreditsBalance())
                .monthlyCredits(subscription.getMonthlyCredits())
                .nextRenewalDate(subscription.getNextRenewalDate())
                .autoRenew(subscription.isAutoRenew())
                .build());
    }

    @GetMapping("/usage")
    public ResponseEntity<Page<CreditUsageDTO>> getUsageHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        Pageable pageable = PageRequest.of(page, size, Sort.by("usedAt").descending());
        Page<CreditUsageLog> usageLogs = creditUsageLogRepository.findByUser(user, pageable);

        Page<CreditUsageDTO> usageHistory = usageLogs.map(log -> CreditUsageDTO.builder()
                .id(log.getId())
                .creditsUsed(log.getCreditsUsed())
                .usedAt(log.getUsedAt())
                .build());

        return ResponseEntity.ok(usageHistory);
    }
}
