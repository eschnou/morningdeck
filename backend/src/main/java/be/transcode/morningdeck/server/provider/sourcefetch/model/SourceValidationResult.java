package be.transcode.morningdeck.server.provider.sourcefetch.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceValidationResult {
    private boolean valid;
    private String feedTitle;
    private String feedDescription;
    private String errorMessage;

    public static SourceValidationResult success(String feedTitle, String feedDescription) {
        return SourceValidationResult.builder()
                .valid(true)
                .feedTitle(feedTitle)
                .feedDescription(feedDescription)
                .build();
    }

    public static SourceValidationResult failure(String errorMessage) {
        return SourceValidationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .build();
    }
}
