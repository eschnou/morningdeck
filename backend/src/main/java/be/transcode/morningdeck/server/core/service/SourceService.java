package be.transcode.morningdeck.server.core.service;

import be.transcode.morningdeck.server.core.dto.SourceDTO;
import be.transcode.morningdeck.server.core.exception.DuplicateSourceException;
import be.transcode.morningdeck.server.core.exception.ResourceNotFoundException;
import be.transcode.morningdeck.server.core.exception.SourceValidationException;
import be.transcode.morningdeck.server.core.model.DayBrief;
import be.transcode.morningdeck.server.core.model.FetchStatus;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceStatus;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.core.queue.FetchQueue;
import be.transcode.morningdeck.server.core.repository.DayBriefRepository;
import be.transcode.morningdeck.server.core.repository.NewsItemRepository;
import be.transcode.morningdeck.server.core.repository.SourceRepository;
import be.transcode.morningdeck.server.core.util.UrlValidator;
import be.transcode.morningdeck.server.provider.sourcefetch.SourceFetcher;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;
    private final DayBriefRepository dayBriefRepository;
    private final NewsItemRepository newsItemRepository;
    private final List<SourceFetcher> sourceFetchers;
    private final UrlValidator urlValidator;

    @Autowired(required = false)
    private FetchQueue fetchQueue;

    @Value("${application.email.domain:inbound.morningdeck.com}")
    private String emailDomain;

    /**
     * Create a source attached to a briefing.
     */
    @Transactional
    public SourceDTO createSource(UUID userId, UUID briefingId, String url, String name, SourceType type,
                                  List<String> tags, Integer refreshIntervalMinutes, String extractionPrompt) {
        // Validate briefing exists and belongs to user
        DayBrief dayBrief = dayBriefRepository.findById(briefingId)
                .orElseThrow(() -> new ResourceNotFoundException("Briefing not found"));
        if (!dayBrief.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Briefing not found");
        }

        Source source;
        if (type == SourceType.EMAIL) {
            // EMAIL sources: require name, generate email address, no URL validation
            if (name == null || name.isBlank()) {
                throw new SourceValidationException("Name is required for email sources");
            }

            UUID emailUuid = UUID.randomUUID();
            String emailUrl = "email://" + emailUuid;

            // Check for duplicates (using the generated URL)
            if (sourceRepository.existsByDayBriefIdAndUrl(briefingId, emailUrl)) {
                throw new DuplicateSourceException(emailUrl);
            }

            source = Source.builder()
                    .dayBrief(dayBrief)
                    .url(emailUrl)
                    .name(name)
                    .type(SourceType.EMAIL)
                    .emailAddress(emailUuid)
                    .status(SourceStatus.ACTIVE)
                    .tags(tags)
                    .refreshIntervalMinutes(0) // No refresh for email sources
                    .build();
        } else if (type == SourceType.WEB) {
            // WEB sources: require URL and extraction prompt
            urlValidator.validate(url);

            if (extractionPrompt == null || extractionPrompt.isBlank()) {
                throw new SourceValidationException("Extraction prompt is required for web sources");
            }

            // Check for duplicates within this briefing
            if (sourceRepository.existsByDayBriefIdAndUrl(briefingId, url)) {
                throw new DuplicateSourceException(url);
            }

            // Validate URL is reachable
            SourceFetcher fetcher = findFetcher(type);
            SourceValidationResult validationResult = fetcher.validate(url);

            if (!validationResult.isValid()) {
                throw new SourceValidationException(validationResult.getErrorMessage());
            }

            // Use auto-detected name if not provided
            String sourceName = (name != null && !name.isBlank())
                    ? name
                    : validationResult.getFeedTitle();

            source = Source.builder()
                    .dayBrief(dayBrief)
                    .url(url)
                    .name(sourceName)
                    .type(SourceType.WEB)
                    .extractionPrompt(extractionPrompt)
                    .status(SourceStatus.ACTIVE)
                    .tags(tags)
                    .refreshIntervalMinutes(refreshIntervalMinutes != null ? refreshIntervalMinutes : 60)
                    .build();
        } else if (type == SourceType.REDDIT) {
            // REDDIT sources: require subreddit name
            if (url == null || url.isBlank()) {
                throw new SourceValidationException("Subreddit name is required for Reddit sources");
            }

            // Normalize URL format
            String normalizedUrl = url.startsWith("reddit://") ? url : "reddit://" + url;

            // Check for duplicates within this briefing
            if (sourceRepository.existsByDayBriefIdAndUrl(briefingId, normalizedUrl)) {
                throw new DuplicateSourceException(normalizedUrl);
            }

            // Validate subreddit exists
            SourceFetcher fetcher = findFetcher(type);
            SourceValidationResult validationResult = fetcher.validate(normalizedUrl);

            if (!validationResult.isValid()) {
                throw new SourceValidationException(validationResult.getErrorMessage());
            }

            // Use auto-detected name if not provided
            String sourceName = (name != null && !name.isBlank())
                    ? name
                    : validationResult.getFeedTitle();

            source = Source.builder()
                    .dayBrief(dayBrief)
                    .url(normalizedUrl)
                    .name(sourceName)
                    .type(SourceType.REDDIT)
                    .status(SourceStatus.ACTIVE)
                    .tags(tags)
                    .refreshIntervalMinutes(refreshIntervalMinutes != null ? refreshIntervalMinutes : 30)
                    .build();
        } else {
            // RSS sources
            urlValidator.validate(url);

            // Check for duplicates within this briefing
            if (sourceRepository.existsByDayBriefIdAndUrl(briefingId, url)) {
                throw new DuplicateSourceException(url);
            }

            // Validate feed and get metadata
            SourceFetcher fetcher = findFetcher(type);
            SourceValidationResult validationResult = fetcher.validate(url);

            if (!validationResult.isValid()) {
                throw new SourceValidationException(validationResult.getErrorMessage());
            }

            // Use auto-detected name if not provided
            String sourceName = (name != null && !name.isBlank())
                    ? name
                    : validationResult.getFeedTitle();

            source = Source.builder()
                    .dayBrief(dayBrief)
                    .url(url)
                    .name(sourceName)
                    .type(type)
                    .status(SourceStatus.ACTIVE)
                    .tags(tags)
                    .refreshIntervalMinutes(refreshIntervalMinutes != null ? refreshIntervalMinutes : 15)
                    .build();
        }

        source = sourceRepository.save(source);
        log.info("Created {} source {} for briefing {} user {}", type, source.getId(), briefingId, userId);

        return mapToDTO(source, 0L, 0L);
    }

    @Transactional(readOnly = true)
    public SourceDTO getSource(UUID userId, UUID sourceId) {
        Source source = getSourceWithOwnershipCheck(userId, sourceId);
        long itemCount = newsItemRepository.countBySourceId(sourceId);
        long unreadCount = newsItemRepository.countBySourceIdAndReadAtIsNull(sourceId);
        return mapToDTO(source, itemCount, unreadCount);
    }

    /**
     * List all sources for a user (across all briefings).
     */
    @Transactional(readOnly = true)
    public Page<SourceDTO> listSources(UUID userId, SourceStatus status, Pageable pageable) {
        Page<Source> sources;
        if (status != null) {
            sources = sourceRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            sources = sourceRepository.findByUserId(userId, pageable);
        }

        return sources.map(source -> {
            long itemCount = newsItemRepository.countBySourceId(source.getId());
            long unreadCount = newsItemRepository.countBySourceIdAndReadAtIsNull(source.getId());
            return mapToDTO(source, itemCount, unreadCount);
        });
    }

    /**
     * List sources for a specific briefing.
     */
    @Transactional(readOnly = true)
    public Page<SourceDTO> listSourcesForBriefing(UUID userId, UUID briefingId, SourceStatus status, Pageable pageable) {
        // Validate briefing belongs to user
        DayBrief dayBrief = dayBriefRepository.findById(briefingId)
                .orElseThrow(() -> new ResourceNotFoundException("Briefing not found"));
        if (!dayBrief.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Briefing not found");
        }

        Page<Source> sources;
        if (status != null) {
            sources = sourceRepository.findByDayBriefIdAndStatus(briefingId, status, pageable);
        } else {
            sources = sourceRepository.findByDayBriefId(briefingId, pageable);
        }

        return sources.map(source -> {
            long itemCount = newsItemRepository.countBySourceId(source.getId());
            long unreadCount = newsItemRepository.countBySourceIdAndReadAtIsNull(source.getId());
            return mapToDTO(source, itemCount, unreadCount);
        });
    }

    @Transactional
    public SourceDTO updateSource(UUID userId, UUID sourceId, String name, List<String> tags,
                                  SourceStatus status, Integer refreshIntervalMinutes, String extractionPrompt) {
        Source source = getSourceWithOwnershipCheck(userId, sourceId);

        if (name != null && !name.isBlank()) {
            source.setName(name);
        }
        if (tags != null) {
            source.setTags(tags);
        }
        if (status != null) {
            // Only allow ACTIVE or PAUSED status updates
            if (status == SourceStatus.ACTIVE || status == SourceStatus.PAUSED) {
                source.setStatus(status);
            }
        }
        if (refreshIntervalMinutes != null && refreshIntervalMinutes > 0) {
            source.setRefreshIntervalMinutes(refreshIntervalMinutes);
        }
        // Only allow updating extraction prompt for WEB sources
        if (extractionPrompt != null && source.getType() == SourceType.WEB) {
            source.setExtractionPrompt(extractionPrompt);
        }

        source = sourceRepository.save(source);
        log.info("Updated source {} for user {}", sourceId, userId);

        long itemCount = newsItemRepository.countBySourceId(sourceId);
        long unreadCount = newsItemRepository.countBySourceIdAndReadAtIsNull(sourceId);
        return mapToDTO(source, itemCount, unreadCount);
    }

    @Transactional
    public void deleteSource(UUID userId, UUID sourceId) {
        Source source = getSourceWithOwnershipCheck(userId, sourceId);

        // Delete source (cascades to NewsItems via JPA)
        sourceRepository.delete(source);
        log.info("Deleted source {} and its items for user {}", sourceId, userId);
    }

    public SourceValidationResult validateSource(String url, SourceType type) {
        urlValidator.validate(url);
        SourceFetcher fetcher = findFetcher(type);
        return fetcher.validate(url);
    }

    /**
     * Force refresh a source immediately.
     */
    @Transactional
    public SourceDTO refreshSource(UUID userId, UUID sourceId) {
        Source source = getSourceWithOwnershipCheck(userId, sourceId);

        // Can only refresh active sources
        if (source.getStatus() != SourceStatus.ACTIVE) {
            throw new SourceValidationException("Cannot refresh source with status: " + source.getStatus());
        }

        // Check if already being fetched
        if (source.getFetchStatus() == FetchStatus.QUEUED || source.getFetchStatus() == FetchStatus.FETCHING) {
            log.info("Source {} is already being fetched, status={}", sourceId, source.getFetchStatus());
            long itemCount = newsItemRepository.countBySourceId(sourceId);
            long unreadCount = newsItemRepository.countBySourceIdAndReadAtIsNull(sourceId);
            return mapToDTO(source, itemCount, unreadCount);
        }

        log.info("Force refresh requested for source {} by user {}", sourceId, userId);

        if (fetchQueue == null) {
            throw new SourceValidationException("Feed refresh is not enabled");
        }

        source.setFetchStatus(FetchStatus.QUEUED);
        source.setQueuedAt(Instant.now());
        sourceRepository.save(source);

        boolean enqueued = fetchQueue.enqueue(source.getId());
        if (!enqueued) {
            // Queue full - reset status
            source.setFetchStatus(FetchStatus.IDLE);
            source.setQueuedAt(null);
            sourceRepository.save(source);
            throw new SourceValidationException("Refresh queue is full, please try again later");
        }

        log.info("Source {} enqueued for refresh", sourceId);

        long itemCount = newsItemRepository.countBySourceId(sourceId);
        long unreadCount = newsItemRepository.countBySourceIdAndReadAtIsNull(sourceId);
        return mapToDTO(source, itemCount, unreadCount);
    }

    // Internal method to get source entity
    public Source getSourceEntity(UUID userId, UUID sourceId) {
        return getSourceWithOwnershipCheck(userId, sourceId);
    }

    private Source getSourceWithOwnershipCheck(UUID userId, UUID sourceId) {
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Source not found"));

        // Check ownership via the parent briefing
        if (!source.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Source not found");
        }

        return source;
    }

    private SourceFetcher findFetcher(SourceType type) {
        return sourceFetchers.stream()
                .filter(f -> f.getSourceType() == type)
                .findFirst()
                .orElseThrow(() -> new SourceValidationException("Unsupported source type: " + type));
    }

    private SourceDTO mapToDTO(Source source, Long itemCount, Long unreadCount) {
        String formattedEmailAddress = null;
        if (source.getEmailAddress() != null) {
            formattedEmailAddress = source.getEmailAddress() + "@" + emailDomain;
        }

        return SourceDTO.builder()
                .id(source.getId())
                .briefingId(source.getDayBrief().getId())
                .briefingTitle(source.getDayBrief().getTitle())
                .url(source.getUrl())
                .extractionPrompt(source.getExtractionPrompt())
                .name(source.getName())
                .emailAddress(formattedEmailAddress)
                .type(source.getType())
                .status(source.getStatus())
                .tags(source.getTags())
                .lastFetchedAt(source.getLastFetchedAt())
                .lastError(source.getLastError())
                .fetchStatus(source.getFetchStatus())
                .refreshIntervalMinutes(source.getRefreshIntervalMinutes())
                .itemCount(itemCount)
                .unreadCount(unreadCount)
                .createdAt(source.getCreatedAt())
                .build();
    }
}
