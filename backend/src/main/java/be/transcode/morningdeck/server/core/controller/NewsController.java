package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.core.dto.NewsItemDTO;
import be.transcode.morningdeck.server.core.model.User;
import be.transcode.morningdeck.server.core.service.NewsItemService;
import be.transcode.morningdeck.server.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsItemService newsItemService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<Page<NewsItemDTO>> searchNews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String readStatus,
            @RequestParam(required = false) Boolean saved,
            @PageableDefault(size = 20, sort = "publishedAt") Pageable pageable) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());

        Page<NewsItemDTO> newsItems = newsItemService.searchNewsItems(
                user.getId(),
                q,
                sourceId,
                from,
                to,
                readStatus,
                saved,
                pageable
        );

        return ResponseEntity.ok(newsItems);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NewsItemDTO> getNewsItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        NewsItemDTO newsItem = newsItemService.getNewsItem(user.getId(), id);

        return ResponseEntity.ok(newsItem);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NewsItemDTO> toggleRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        NewsItemDTO newsItem = newsItemService.toggleRead(user.getId(), id);

        return ResponseEntity.ok(newsItem);
    }

    @PatchMapping("/{id}/saved")
    public ResponseEntity<NewsItemDTO> toggleSaved(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {

        User user = userService.getInternalUserByUsername(userDetails.getUsername());
        NewsItemDTO newsItem = newsItemService.toggleSaved(user.getId(), id);

        return ResponseEntity.ok(newsItem);
    }
}
