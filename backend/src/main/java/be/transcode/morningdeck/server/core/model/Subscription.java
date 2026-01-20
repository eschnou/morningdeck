package be.transcode.morningdeck.server.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "credits_balance", nullable = false)
    private Integer creditsBalance;

    @Column(name = "monthly_credits", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer monthlyCredits;

    @Column(name = "next_renewal_date", nullable = false)
    private Instant nextRenewalDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToOne(mappedBy = "subscription")
    private User user;

    public enum SubscriptionPlan {
        FREE(1000),
        PRO(10000),
        BUSINESS(50000);

        private final int monthlyCredits;

        SubscriptionPlan(int monthlyCredits) {
            this.monthlyCredits = monthlyCredits;
        }

        public int getMonthlyCredits() {
            return monthlyCredits;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        nextRenewalDate = Instant.now().atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
        creditsBalance = plan.getMonthlyCredits();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
