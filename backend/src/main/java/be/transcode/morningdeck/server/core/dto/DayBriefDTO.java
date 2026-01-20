package be.transcode.morningdeck.server.core.dto;

import be.transcode.morningdeck.server.core.model.BriefingFrequency;
import be.transcode.morningdeck.server.core.model.DayBriefStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DayBriefDTO {
    private UUID id;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Briefing criteria is required")
    private String briefing;

    @NotNull(message = "Frequency is required")
    private BriefingFrequency frequency;

    private DayOfWeek scheduleDayOfWeek;

    @NotNull(message = "Schedule time is required")
    private LocalTime scheduleTime;

    private String timezone;
    private DayBriefStatus status;
    private Instant lastExecutedAt;
    private Integer sourceCount;
    private Instant createdAt;
    private Boolean emailDeliveryEnabled;
    private Integer position;
}
