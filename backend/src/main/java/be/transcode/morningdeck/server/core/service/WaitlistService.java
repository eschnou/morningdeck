package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.model.Waitlist;
import be.transcode.morningdeck.server.core.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WaitlistService {
    private final WaitlistRepository waitlistRepository;

    @Transactional
    public void addToWaitlist(String email) {
        String normalizedEmail = email.toLowerCase().trim();

        if (waitlistRepository.existsByEmail(normalizedEmail)) {
            throw new BadRequestException("Email already on waitlist");
        }

        Waitlist entry = Waitlist.builder()
                .email(normalizedEmail)
                .build();
        waitlistRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public long getCount() {
        return waitlistRepository.count();
    }
}
