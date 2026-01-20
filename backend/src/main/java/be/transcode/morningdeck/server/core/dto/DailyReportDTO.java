package be.transcode.morningdeck.server.core.dto;

import be.transcode.morningdeck.server.core.model.ReportStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyReportDTO {
    private UUID id;
    private UUID dayBriefId;
    private String dayBriefTitle;
    private String dayBriefDescription;
    private Instant generatedAt;
    private ReportStatus status;
    private List<ReportItemDTO> items;
    private Integer itemCount;
}
