package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.core.dto.WaitlistRequest;
import be.transcode.morningdeck.server.core.dto.WaitlistStatsResponse;
import be.transcode.morningdeck.server.core.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping("/join")
    public ResponseEntity<Void> joinWaitlist(@RequestBody @Valid WaitlistRequest request) {
        waitlistService.addToWaitlist(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<WaitlistStatsResponse> getStats() {
        long count = waitlistService.getCount();
        return ResponseEntity.ok(WaitlistStatsResponse.builder()
                .count(count)
                .build());
    }
}
