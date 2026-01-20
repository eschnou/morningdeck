package be.transcode.morningdeck.server.provider.sourcefetch.reddit;

import be.transcode.morningdeck.server.core.exception.SourceFetchException;
import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.provider.sourcefetch.SourceFetcher;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;
import be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto.RedditListingResponse;
import be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto.RedditPost;
import be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto.RedditPostWrapper;
import be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto.RedditTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.reddit", name = "client-id")
public class RedditFetcher implements SourceFetcher {

    private static final String REDDIT_OAUTH_URL = "https://oauth.reddit.com";
    private static final String REDDIT_TOKEN_URL = "https://www.reddit.com/api/v1/access_token";
    private static final Pattern SUBREDDIT_PATTERN = Pattern.compile("^[A-Za-z0-9_]{2,21}$");

    private static final Set<String> REDDIT_MEDIA_DOMAINS = Set.of(
            "i.redd.it",
            "v.redd.it",
            "reddit.com",
            "www.reddit.com",
            "old.reddit.com",
            "new.reddit.com",
            "preview.redd.it",
            "i.imgur.com",
            "imgur.com"
    );

    private final RestTemplate restTemplate;
    private final RedditConfig config;

    private String cachedAccessToken;
    private long tokenExpiresAt;

    @Override
    public SourceType getSourceType() {
        return SourceType.REDDIT;
    }

    @Override
    public SourceValidationResult validate(String url) {
        String subreddit = extractSubreddit(url);
        if (subreddit == null) {
            return SourceValidationResult.failure(
                    "Invalid subreddit format. Use: reddit://<subreddit_name> or just <subreddit_name>");
        }

        if (!SUBREDDIT_PATTERN.matcher(subreddit).matches()) {
            return SourceValidationResult.failure(
                    "Invalid subreddit name: must be 2-21 characters, alphanumeric and underscores only");
        }

        try {
            fetchSubredditPosts(subreddit, "hot", 1);
            return SourceValidationResult.success(
                    "r/" + subreddit,
                    "Reddit subreddit: " + subreddit
            );
        } catch (Exception e) {
            log.warn("Failed to validate subreddit {}: {}", subreddit, e.getMessage());
            return SourceValidationResult.failure(
                    "Subreddit not found or inaccessible: " + subreddit);
        }
    }

    @Override
    public List<FetchedItem> fetch(Source source, Instant lastFetchedAt) {
        String subreddit = extractSubreddit(source.getUrl());
        if (subreddit == null) {
            throw new SourceFetchException("Invalid subreddit URL: " + source.getUrl());
        }

        try {
            RedditListingResponse response = fetchSubredditPosts(
                    subreddit, "hot", config.getDefaultLimit());

            List<FetchedItem> items = new ArrayList<>();
            Instant cutoffTime = calculateCutoffTime(lastFetchedAt);

            if (response.getData() == null || response.getData().getChildren() == null) {
                log.warn("Empty response from Reddit API for r/{}", subreddit);
                return items;
            }

            for (RedditPostWrapper wrapper : response.getData().getChildren()) {
                RedditPost post = wrapper.getData();

                // Filter: only link posts (not self-posts)
                if (post.isSelf()) {
                    continue;
                }

                // Filter: skip stickied posts
                if (post.isStickied()) {
                    continue;
                }

                // Filter: skip NSFW posts
                if (post.isOver18()) {
                    continue;
                }

                // Filter: skip Reddit-hosted media (external links only)
                if (isRedditMedia(post.getDomain())) {
                    continue;
                }

                Instant publishedAt = epochToInstant(post.getCreatedUtc());

                // Filter: skip posts older than cutoff
                if (cutoffTime != null && publishedAt.isBefore(cutoffTime)) {
                    continue;
                }

                FetchedItem item = FetchedItem.builder()
                        .guid("reddit:" + post.getName())
                        .title(post.getTitle())
                        .link(post.getUrl())
                        .author("u/" + post.getAuthor())
                        .publishedAt(publishedAt)
                        .rawContent(buildRawContent(post))
                        .cleanContent(buildCleanContent(post))
                        .build();

                items.add(item);
            }

            log.info("Fetched {} link posts from r/{} for source {}", items.size(), subreddit, source.getId());
            return items;

        } catch (SourceFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceFetchException(
                    "Failed to fetch Reddit posts from r/" + subreddit + ": " + e.getMessage(), e);
        }
    }

    private String extractSubreddit(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        // Support both "reddit://subreddit" and plain "subreddit"
        if (url.startsWith("reddit://")) {
            return url.substring("reddit://".length()).trim();
        }
        return url.trim();
    }

    private boolean isRedditMedia(String domain) {
        if (domain == null) {
            return false;
        }
        return REDDIT_MEDIA_DOMAINS.contains(domain.toLowerCase());
    }

    private RedditListingResponse fetchSubredditPosts(String subreddit, String sort, int limit) {
        String accessToken = getValidAccessToken();

        String url = UriComponentsBuilder
                .fromHttpUrl(REDDIT_OAUTH_URL + "/r/" + subreddit + "/" + sort)
                .queryParam("limit", limit)
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("User-Agent", config.getUserAgent());

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<RedditListingResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, RedditListingResponse.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new SourceFetchException("Reddit API returned: " + response.getStatusCode());
        }

        return response.getBody();
    }

    private synchronized String getValidAccessToken() {
        // Check if we have a valid cached token (with 60 second buffer)
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiresAt - 60000) {
            return cachedAccessToken;
        }

        log.debug("Refreshing Reddit access token");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getClientId(), config.getClientSecret());
        headers.set("User-Agent", config.getUserAgent());

        String body = "grant_type=client_credentials";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<RedditTokenResponse> response = restTemplate.exchange(
                REDDIT_TOKEN_URL, HttpMethod.POST, entity, RedditTokenResponse.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new SourceFetchException("Failed to obtain Reddit access token: " + response.getStatusCode());
        }

        RedditTokenResponse tokenResponse = response.getBody();
        cachedAccessToken = tokenResponse.getAccessToken();
        tokenExpiresAt = System.currentTimeMillis() + (tokenResponse.getExpiresIn() * 1000L);

        log.debug("Obtained new Reddit access token, expires in {} seconds", tokenResponse.getExpiresIn());

        return cachedAccessToken;
    }

    private Instant calculateCutoffTime(Instant lastFetchedAt) {
        Instant maxAgeCutoff = Instant.now().atZone(ZoneOffset.UTC).minusHours(config.getMaxAgeHours()).toInstant();

        if (lastFetchedAt == null) {
            return maxAgeCutoff;
        }

        // Use the more recent of lastFetchedAt or maxAge cutoff
        return lastFetchedAt.isAfter(maxAgeCutoff) ? lastFetchedAt : maxAgeCutoff;
    }

    private Instant epochToInstant(double epochSeconds) {
        return Instant.ofEpochSecond((long) epochSeconds);
    }

    private String buildRawContent(RedditPost post) {
        return String.format(
                "Posted to r/%s by u/%s\nScore: %d | Comments: %d\nLink: %s",
                post.getSubreddit(),
                post.getAuthor(),
                post.getScore(),
                post.getNumComments(),
                post.getUrl()
        );
    }

    private String buildCleanContent(RedditPost post) {
        return String.format(
                "**r/%s** - %d points, %d comments\n\n[%s](%s)",
                post.getSubreddit(),
                post.getScore(),
                post.getNumComments(),
                post.getTitle(),
                post.getUrl()
        );
    }
}
