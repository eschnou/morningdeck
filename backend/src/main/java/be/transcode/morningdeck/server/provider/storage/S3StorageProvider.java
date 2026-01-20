package be.transcode.morningdeck.server.provider.storage;

import be.transcode.morningdeck.server.core.exception.BadRequestException;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.storage", name = "provider", havingValue = "s3", matchIfMissing = false)
public class S3StorageProvider implements StorageProvider {

    private final S3Template s3Template;

    @Value("${application.storage.s3.bucket}")
    private String bucketName;

    private static final String[] SUPPORTED_TYPES = {
            "image/jpeg",
            "image/png",
            "image/gif",
            "audio/mpeg"
    };

    @Override
    public void store(UUID fileId, byte[] file, String contentType) {
        validateContentType(contentType);
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(file);
            ObjectMetadata metadata = ObjectMetadata.builder()
                    .contentType(contentType)
                    .contentLength((long) file.length)
                    .build();

            s3Template.upload(bucketName, fileId.toString(), inputStream, metadata);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store file in S3", e);
        }
    }

    @Override
    public void delete(UUID fileId) {
        try {
            // Check if object exists first
            if (!s3Template.objectExists(bucketName, fileId.toString())) {
                throw new ResourceNotFoundException("Avatar not found");
            }
            s3Template.deleteObject(bucketName, fileId.toString());
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file from S3", e);
        }
    }

    @Override
    public byte[] getFileContent(UUID fileId) {
        try {
            Resource resource = s3Template.download(bucketName, fileId.toString());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("Image not found");
            }
            return resource.getContentAsByteArray();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file from S3", e);
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
