package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.dto.DailyReportDTO;
import be.transcode.morningdeck.server.core.dto.ReportItemDTO;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.model.DailyReport;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.ReportItem;
import be.transcode.morningdeck.server.core.repository.DailyReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final DailyReportRepository dailyReportRepository;
    private final DayBriefService dayBriefService;

    @Transactional(readOnly = true)
    public DailyReportDTO getReport(UUID userId, UUID dayBriefId, UUID reportId) {
        // Verify user owns the daybrief
        dayBriefService.getDayBriefEntity(userId, dayBriefId);

        DailyReport report = dailyReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        if (!report.getDayBrief().getId().equals(dayBriefId)) {
            throw new ResourceNotFoundException("Report not found");
        }

        return mapToDTO(report, true);
    }

    @Transactional(readOnly = true)
    public Page<DailyReportDTO> listReports(UUID userId, UUID dayBriefId, Pageable pageable) {
        // Verify user owns the daybrief
        dayBriefService.getDayBriefEntity(userId, dayBriefId);

        Page<DailyReport> reports = dailyReportRepository.findByDayBriefIdOrderByGeneratedAtDesc(dayBriefId, pageable);
        return reports.map(report -> mapToDTO(report, false));
    }

    @Transactional
    public void deleteReport(UUID userId, UUID dayBriefId, UUID reportId) {
        // Verify user owns the daybrief
        dayBriefService.getDayBriefEntity(userId, dayBriefId);

        DailyReport report = dailyReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        if (!report.getDayBrief().getId().equals(dayBriefId)) {
            throw new ResourceNotFoundException("Report not found");
        }

        dailyReportRepository.delete(report);
        log.info("Deleted report {} for daybrief {}", reportId, dayBriefId);
    }

    private DailyReportDTO mapToDTO(DailyReport report, boolean includeItems) {
        DayBrief dayBrief = report.getDayBrief();

        DailyReportDTO.DailyReportDTOBuilder builder = DailyReportDTO.builder()
                .id(report.getId())
                .dayBriefId(dayBrief.getId())
                .dayBriefTitle(dayBrief.getTitle())
                .dayBriefDescription(dayBrief.getDescription())
                .generatedAt(report.getGeneratedAt())
                .status(report.getStatus())
                .itemCount(report.getItems().size());

        if (includeItems) {
            List<ReportItemDTO> itemDTOs = report.getItems().stream()
                    .map(this::mapItemToDTO)
                    .toList();
            builder.items(itemDTOs);
        }

        return builder.build();
    }

    private ReportItemDTO mapItemToDTO(ReportItem item) {
        return ReportItemDTO.builder()
                .newsItemId(item.getNewsItem().getId())
                .title(item.getNewsItem().getTitle())
                .summary(item.getNewsItem().getSummary())
                .link(item.getNewsItem().getLink())
                .publishedAt(item.getNewsItem().getPublishedAt())
                .score(item.getScore())
                .position(item.getPosition())
                .sourceName(item.getNewsItem().getSource().getName())
                .build();
    }
}
