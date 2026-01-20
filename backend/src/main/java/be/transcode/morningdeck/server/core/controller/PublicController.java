package be.transcode.morningdeck.server.core.controller;

import be.transcode.morningdeck.server.config.EmailVerificationProperties;
import be.transcode.morningdeck.server.core.dto.PublicConfigDTO;
import be.transcode.morningdeck.server.provider.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Controller for publicly accessible endpoints that don't require authentication.
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final StorageProvider storageProvider;
    private final EmailVerificationProperties emailVerificationProperties;

    @Value("${application.self-hosted-mode:false}")
    private boolean selfHostedMode;

    @GetMapping("/config")
    public PublicConfigDTO getPublicConfig() {
        return PublicConfigDTO.builder()
                .emailVerificationEnabled(emailVerificationProperties.isEnabled())
                .selfHostedMode(selfHostedMode)
                .build();
    }

    @GetMapping("/avatars/{id}")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID id) {
        byte[] content = storageProvider.getFileContent(id);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.IMAGE_PNG)
                .body(content);
    }
}
