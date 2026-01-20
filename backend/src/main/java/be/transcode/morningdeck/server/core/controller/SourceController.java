package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.core.dto.BulkUpdateResult;
import be.transcode.morningdeck.server.core.dto.SourceDTO;
import be.transcode.morningdeck.server.core.model.SourceStatus;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.service.NewsItemService;
import be.transcode.morningdeck.server.core.service.SourceService;
import be.transcode.morningdeck.server.core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/sources")
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;
    private final NewsItemService newsItemService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<SourceDTO> createSource(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SourceDTO request) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        if (request.getBriefingId() == null) {
            throw new IllegalArgumentException("briefingId is required");
        }

        // Default to RSS if type not specified
        SourceType type = request.getType() != null ? request.getType() : SourceType.RSS;

        SourceDTO source = sourceService.createSource(
                user.getId(),
                request.getBriefingId(),
                request.getUrl(),
                request.getName(),
                type,
                request.getTags(),
                request.getRefreshIntervalMinutes(),
                request.getExtractionPrompt()
        );

        return ResponseEntity.ok(source);
    }

    @GetMapping
    public ResponseEntity<Page<SourceDTO>> listSources(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) SourceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        Page<SourceDTO> sources = sourceService.listSources(user.getId(), status, pageable);

        return ResponseEntity.ok(sources);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SourceDTO> getSource(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        SourceDTO source = sourceService.getSource(user.getId(), id);

        return ResponseEntity.ok(source);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SourceDTO> updateSource(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @RequestBody SourceDTO request) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        SourceDTO source = sourceService.updateSource(
                user.getId(),
                id,
                request.getName(),
                request.getTags(),
                request.getStatus(),
                request.getRefreshIntervalMinutes(),
                request.getExtractionPrompt()
        );

        return ResponseEntity.ok(source);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSource(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        sourceService.deleteSource(user.getId(), id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refresh")
    public ResponseEntity<SourceDTO> refreshSource(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        SourceDTO source = sourceService.refreshSource(user.getId(), id);

        return ResponseEntity.ok(source);
    }

    @PostMapping("/{id}/mark-all-read")
    public ResponseEntity<BulkUpdateResult> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        int count = newsItemService.markAllAsReadBySource(user.getId(), id);

        return ResponseEntity.ok(BulkUpdateResult.builder()
                .updatedCount(count)
                .build());
    }
}
