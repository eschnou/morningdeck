package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.config.AppConfig;
import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.model.InviteCode;
import be.transcode.morningdeck.server.core.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteCodeService {

    private static final String INVALID_CODE_MESSAGE = "Invalid or expired invite code";

    private final InviteCodeRepository inviteCodeRepository;
    private final AppConfig appConfig;

    public boolean isClosedBeta() {
        return appConfig.isClosedBeta();
    }

    @Transactional
    public InviteCode validateAndUse(String code) {
        if (code == null || code.isBlank()) {
            log.warn("Attempted registration with empty invite code");
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }

        String normalizedCode = code.toUpperCase().trim();

        InviteCode inviteCode = inviteCodeRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> {
                    log.warn("Attempted registration with unknown invite code: {}", normalizedCode);
                    return new BadRequestException(INVALID_CODE_MESSAGE);
                });

        if (!inviteCode.isEnabled()) {
            log.warn("Attempted registration with disabled invite code: {}", normalizedCode);
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }

        if (inviteCode.isExpired()) {
            log.warn("Attempted registration with expired invite code: {}", normalizedCode);
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }

        if (inviteCode.getMaxUses() != null && inviteCode.getUseCount() >= inviteCode.getMaxUses()) {
            log.warn("Attempted registration with exhausted invite code: {}", normalizedCode);
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }

        // Atomically increment usage count
        inviteCodeRepository.incrementUseCount(inviteCode.getId());

        log.info("Invite code {} used successfully, new count: {}", normalizedCode, inviteCode.getUseCount() + 1);

        return inviteCode;
    }
}
