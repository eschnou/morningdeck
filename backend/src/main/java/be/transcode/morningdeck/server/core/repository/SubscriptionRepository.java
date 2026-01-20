package be.transcode.morningdeck.server.core.repository;

import be.transcode.morningdeck.server.core.model.Subscription;
import be.transcode.morningdeck.server.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByAutoRenewTrueAndNextRenewalDateBefore(Instant date);

    Optional<Subscription> findByUser(User user);

    @Query("SELECT s.creditsBalance FROM Subscription s WHERE s.user.id = :userId")
    Optional<Integer> findCreditsBalanceByUserId(@Param("userId") UUID userId);

    @Query("SELECT s.user.id FROM Subscription s WHERE s.creditsBalance > 0")
    Set<UUID> findUserIdsWithCredits();

}
