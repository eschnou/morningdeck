package be.transcode.morningdeck.server.provider.storage;

import be.transcode.morningdeck.server.config.AppConfig;
import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageProvider implements StorageProvider {

    private final AppConfig appConfig;

    private static final String[] SUPPORTED_TYPES = {
            "image/jpeg",
            "image/png",
            "image/gif",
            "audio/mpeg"
    };

    @Override
    public void store(UUID fileId, byte[] file, String contentType) {
        validateContentType(contentType);
        Path directory = Paths.get(appConfig.getUploadFolder());
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileId.toString()), file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public void delete(UUID fileId) {
        Path filePath = Paths.get(appConfig.getUploadFolder(), fileId.toString());
        try {
            if (!Files.deleteIfExists(filePath)) {
                throw new ResourceNotFoundException("Avatar not found");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    @Override
    public byte[] getFileContent(UUID fileId) {
        Path filePath = Paths.get(appConfig.getUploadFolder(), fileId.toString());
        try {
            if (!Files.exists(filePath)) {
                throw new ResourceNotFoundException("Image not found");
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file", e);
        }
    }

    @Override
    public void validateContentType(String contentType) {
        boolean isSupported = false;
        for (String supportedType : SUPPORTED_TYPES) {
            if (supportedType.equals(contentType)) {
                isSupported = true;
                break;
            }
        }

        if (!isSupported) {
            throw new BadRequestException("Unsupported file type. Supported types are: " + String.join(", ", SUPPORTED_TYPES));
        }
    }
}
