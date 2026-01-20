package be.transcode.morningdeck.server.provider.storage;

import java.util.UUID;

public interface StorageProvider {
    /**
     * Stores a file for a specific user
     * @param fileId The user's ID
     * @param file The file to store
     * @param contentType The content type of the file
     */
    void store(UUID fileId, byte[] file, String contentType);

    /**
     * Deletes a user's file
     * @param fileId The user's ID
     */
    void delete(UUID fileId);

    /**
     * Gets the file content for a user
     * @param fileId The user's ID
     * @return The file content
     */
    byte[] getFileContent(UUID fileId);

    /**
     * Validates if the file type is supported
     * @param contentType The content type to validate
     */
    void validateContentType(String contentType);
}
