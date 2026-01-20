package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.dto.DayBriefDTO;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.model.BriefingFrequency;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.DayBriefStatus;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DayBriefService {

    private final DayBriefRepository dayBriefRepository;

    @Transactional
    public DayBriefDTO createDayBrief(
            UUID userId,
            String title,
            String description,
            String briefing,
            BriefingFrequency frequency,
            DayOfWeek scheduleDayOfWeek,
            LocalTime scheduleTime,
            String timezone,
            Boolean emailDeliveryEnabled) {

        Integer maxPosition = dayBriefRepository.findMaxPositionByUserId(userId);

        DayBrief dayBrief = DayBrief.builder()
                .userId(userId)
                .title(title)
                .description(description)
                .briefing(briefing)
                .frequency(frequency)
                .scheduleDayOfWeek(scheduleDayOfWeek)
                .scheduleTime(scheduleTime)
                .timezone(timezone != null ? timezone : "UTC")
                .status(DayBriefStatus.ACTIVE)
                .emailDeliveryEnabled(emailDeliveryEnabled == null || emailDeliveryEnabled)
                .position(maxPosition + 1)
                .build();

        dayBrief = dayBriefRepository.save(dayBrief);
        log.info("Created DayBrief {} for user {}", dayBrief.getId(), userId);

        return mapToDTO(dayBrief);
    }

    @Transactional(readOnly = true)
    public DayBriefDTO getDayBrief(UUID userId, UUID dayBriefId) {
        DayBrief dayBrief = getDayBriefWithOwnershipCheck(userId, dayBriefId);
        return mapToDTO(dayBrief);
    }

    @Transactional(readOnly = true)
    public Page<DayBriefDTO> listDayBriefs(UUID userId, DayBriefStatus status, Pageable pageable) {
        Page<DayBrief> dayBriefs;
        if (status != null) {
            dayBriefs = dayBriefRepository.findByUserIdAndStatusOrderByPositionAsc(userId, status, pageable);
        } else {
            dayBriefs = dayBriefRepository.findByUserIdOrderByPositionAsc(userId, pageable);
        }
        return dayBriefs.map(this::mapToDTO);
    }

    @Transactional
    public DayBriefDTO updateDayBrief(
            UUID userId,
            UUID dayBriefId,
            String title,
            String description,
            String briefing,
            BriefingFrequency frequency,
            DayOfWeek scheduleDayOfWeek,
            LocalTime scheduleTime,
            String timezone,
            DayBriefStatus status,
            Boolean emailDeliveryEnabled) {

        DayBrief dayBrief = getDayBriefWithOwnershipCheck(userId, dayBriefId);

        if (title != null && !title.isBlank()) {
            dayBrief.setTitle(title);
        }
        if (description != null) {
            dayBrief.setDescription(description);
        }
        if (briefing != null && !briefing.isBlank()) {
            dayBrief.setBriefing(briefing);
        }
        if (frequency != null) {
            dayBrief.setFrequency(frequency);
        }
        if (scheduleDayOfWeek != null) {
            dayBrief.setScheduleDayOfWeek(scheduleDayOfWeek);
        }
        if (scheduleTime != null) {
            dayBrief.setScheduleTime(scheduleTime);
        }
        if (timezone != null) {
            dayBrief.setTimezone(timezone);
        }
        if (status != null) {
            // Only allow ACTIVE or PAUSED status updates
            if (status == DayBriefStatus.ACTIVE || status == DayBriefStatus.PAUSED) {
                dayBrief.setStatus(status);
            }
        }
        if (emailDeliveryEnabled != null) {
            dayBrief.setEmailDeliveryEnabled(emailDeliveryEnabled);
        }

        dayBrief = dayBriefRepository.save(dayBrief);
        log.info("Updated DayBrief {} for user {}", dayBriefId, userId);

        return mapToDTO(dayBrief);
    }

    @Transactional
    public void deleteDayBrief(UUID userId, UUID dayBriefId) {
        DayBrief dayBrief = getDayBriefWithOwnershipCheck(userId, dayBriefId);
        dayBriefRepository.delete(dayBrief);
        log.info("Deleted DayBrief {} for user {}", dayBriefId, userId);
    }

    @Transactional
    public void reorderBriefs(UUID userId, List<UUID> briefIds) {
        List<DayBrief> briefs = dayBriefRepository.findByUserId(userId);

        if (briefs.size() != briefIds.size()) {
            throw new IllegalArgumentException("Request must contain all briefs");
        }

        Map<UUID, DayBrief> briefMap = briefs.stream()
                .collect(Collectors.toMap(DayBrief::getId, Function.identity()));

        for (int i = 0; i < briefIds.size(); i++) {
            DayBrief brief = briefMap.get(briefIds.get(i));
            if (brief == null) {
                throw new IllegalArgumentException("Invalid brief ID: " + briefIds.get(i));
            }
            brief.setPosition(i);
        }

        dayBriefRepository.saveAll(briefs);
        log.info("Reordered {} briefs for user {}", briefIds.size(), userId);
    }

    // Internal method for getting DayBrief entity
    public DayBrief getDayBriefEntity(UUID userId, UUID dayBriefId) {
        return getDayBriefWithOwnershipCheck(userId, dayBriefId);
    }

    /**
     * Updates a day brief's status in a separate transaction.
     * This ensures the status change is immediately visible to other threads/transactions.
     * Used by BriefingSchedulerJob and BriefingWorker.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(UUID dayBriefId, DayBriefStatus status) {
        dayBriefRepository.findById(dayBriefId).ifPresent(dayBrief -> {
            dayBrief.setStatus(status);
            if (status == DayBriefStatus.QUEUED) {
                dayBrief.setQueuedAt(Instant.now());
                dayBrief.setErrorMessage(null);
            } else if (status == DayBriefStatus.ACTIVE) {
                dayBrief.setQueuedAt(null);
                dayBrief.setProcessingStartedAt(null);
            }
            dayBriefRepository.save(dayBrief);
            log.debug("Updated DayBrief {} status to {}", dayBriefId, status);
        });
    }

    private DayBrief getDayBriefWithOwnershipCheck(UUID userId, UUID dayBriefId) {
        DayBrief dayBrief = dayBriefRepository.findById(dayBriefId)
                .orElseThrow(() -> new ResourceNotFoundException("DayBrief not found"));

        if (!dayBrief.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("DayBrief not found");
        }

        return dayBrief;
    }

    private DayBriefDTO mapToDTO(DayBrief dayBrief) {
        return DayBriefDTO.builder()
                .id(dayBrief.getId())
                .title(dayBrief.getTitle())
                .description(dayBrief.getDescription())
                .briefing(dayBrief.getBriefing())
                .frequency(dayBrief.getFrequency())
                .scheduleDayOfWeek(dayBrief.getScheduleDayOfWeek())
                .scheduleTime(dayBrief.getScheduleTime())
                .timezone(dayBrief.getTimezone())
                .status(dayBrief.getStatus())
                .lastExecutedAt(dayBrief.getLastExecutedAt())
                .sourceCount(dayBrief.getSources() != null ? dayBrief.getSources().size() : 0)
                .createdAt(dayBrief.getCreatedAt())
                .emailDeliveryEnabled(dayBrief.isEmailDeliveryEnabled())
                .position(dayBrief.getPosition())
                .build();
    }
}
