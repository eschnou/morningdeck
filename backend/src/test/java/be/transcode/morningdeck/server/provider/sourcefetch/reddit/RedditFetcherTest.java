package be.transcode.morningdeck.server.provider.sourcefetch.reddit;

import be.transcode.morningdeck.server.core.model.Source;
import be.transcode.morningdeck.server.core.model.SourceType;
import be.transcode.morningdeck.server.provider.sourcefetch.model.FetchedItem;
import be.transcode.morningdeck.server.provider.sourcefetch.model.SourceValidationResult;
import be.transcode.morningdeck.server.provider.sourcefetch.reddit.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedditFetcherTest {

    @Mock
    private RestTemplate restTemplate;

    private RedditConfig config;
    private RedditFetcher redditFetcher;

    @BeforeEach
    void setUp() {
        config = new RedditConfig();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setUserAgent("TestAgent/1.0");
        config.setDefaultLimit(25);
        config.setMaxAgeHours(24);

        redditFetcher = new RedditFetcher(restTemplate, config);
    }

    @Test
    void shouldReturnRedditSourceType() {
        assertThat(redditFetcher.getSourceType()).isEqualTo(SourceType.REDDIT);
    }

    @Nested
    class ValidationTests {

        @Test
        void shouldValidateCorrectSubredditFormat() {
            mockTokenResponse();
            mockSubredditResponse("programming", createValidLinkPost());

            SourceValidationResult result = redditFetcher.validate("reddit://programming");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getFeedTitle()).isEqualTo("r/programming");
        }

        @Test
        void shouldValidatePlainSubredditName() {
            mockTokenResponse();
            mockSubredditResponse("java", createValidLinkPost());

            SourceValidationResult result = redditFetcher.validate("java");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getFeedTitle()).isEqualTo("r/java");
        }

        @Test
        void shouldRejectInvalidSubredditName() {
            SourceValidationResult result = redditFetcher.validate("a"); // too short

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("2-21 characters");
        }

        @Test
        void shouldRejectSubredditWithInvalidCharacters() {
            SourceValidationResult result = redditFetcher.validate("invalid-name");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("alphanumeric");
        }

        @Test
        void shouldRejectEmptySubreddit() {
            SourceValidationResult result = redditFetcher.validate("");

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    class FetchTests {

        @Test
        void shouldFilterOutSelfPosts() {
            mockTokenResponse();

            RedditPost selfPost = createValidLinkPost();
            selfPost.setSelf(true);

            RedditPost linkPost = createValidLinkPost();
            linkPost.setId("link1");
            linkPost.setName("t3_link1");
            linkPost.setSelf(false);

            mockSubredditResponse("programming", selfPost, linkPost);

            Source source = createSource("reddit://programming");
            List<FetchedItem> items = redditFetcher.fetch(source, null);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).getGuid()).isEqualTo("reddit:t3_link1");
        }

        @Test
        void shouldFilterOutStickiedPosts() {
            mockTokenResponse();

            RedditPost stickiedPost = createValidLinkPost();
            stickiedPost.setStickied(true);

            RedditPost normalPost = createValidLinkPost();
            normalPost.setId("normal1");
            normalPost.setName("t3_normal1");
            normalPost.setStickied(false);

            mockSubredditResponse("programming", stickiedPost, normalPost);

            Source source = createSource("reddit://programming");
            List<FetchedItem> items = redditFetcher.fetch(source, null);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).getGuid()).isEqualTo("reddit:t3_normal1");
        }

        @Test
        void shouldFilterOutNsfwPosts() {
            mockTokenResponse();

            RedditPost nsfwPost = createValidLinkPost();
            nsfwPost.setOver18(true);

            RedditPost sfwPost = createValidLinkPost();
            sfwPost.setId("sfw1");
            sfwPost.setName("t3_sfw1");
            sfwPost.setOver18(false);

            mockSubredditResponse("programming", nsfwPost, sfwPost);

            Source source = createSource("reddit://programming");
            List<FetchedItem> items = redditFetcher.fetch(source, null);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).getGuid()).isEqualTo("reddit:t3_sfw1");
        }

        @Test
        void shouldFilterOutRedditMediaDomains() {
            mockTokenResponse();

            RedditPost imagePost = createValidLinkPost();
            imagePost.setId("img1");
            imagePost.setDomain("i.redd.it");

            RedditPost videoPost = createValidLinkPost();
            videoPost.setId("vid1");
            videoPost.setDomain("v.redd.it");

            RedditPost externalPost = createValidLinkPost();
            externalPost.setId("ext1");
            externalPost.setName("t3_ext1");
            externalPost.setDomain("example.com");

            mockSubredditResponse("programming", imagePost, videoPost, externalPost);

            Source source = createSource("reddit://programming");
            List<FetchedItem> items = redditFetcher.fetch(source, null);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).getGuid()).isEqualTo("reddit:t3_ext1");
        }

        @Test
        void shouldFilterOutOldPosts() {
            mockTokenResponse();

            // Post from 48 hours ago (older than maxAgeHours of 24)
            RedditPost oldPost = createValidLinkPost();
            oldPost.setId("old1");
            oldPost.setCreatedUtc(Instant.now().minusSeconds(48 * 3600).getEpochSecond());

            // Post from 1 hour ago
            RedditPost recentPost = createValidLinkPost();
            recentPost.setId("recent1");
            recentPost.setName("t3_recent1");
            recentPost.setCreatedUtc(Instant.now().minusSeconds(3600).getEpochSecond());

            mockSubredditResponse("programming", oldPost, recentPost);

            Source source = createSource("reddit://programming");
            List<FetchedItem> items = redditFetcher.fetch(source, null);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).getGuid()).isEqualTo("reddit:t3_recent1");
        }

        @Test
        void shouldBuildCorrectGuid() {
            mockTokenResponse();

            RedditPost post = createValidLinkPost();
            post.setName("t3_abc123");

            mockSubredditResponse("programming", post);

            Source source = createSource("reddit://programming");
            List<FetchedItem> items = redditFetcher.fetch(source, null);

            assertThat(items.get(0).getGuid()).isEqualTo("reddit:t3_abc123");
        }

        @Test
        void shouldMapFieldsCorrectly() {
            mockTokenResponse();

            RedditPost post = createValidLinkPost();
            post.setTitle("Test Article Title");
            post.setUrl("https://example.com/article");
            post.setAuthor("testuser");
            post.setSubreddit("programming");
            post.setScore(100);
            post.setNumComments(50);

            mockSubredditResponse("programming", post);

            Source source = createSource("reddit://programming");
            List<FetchedItem> items = redditFetcher.fetch(source, null);

            FetchedItem item = items.get(0);
            assertThat(item.getTitle()).isEqualTo("Test Article Title");
            assertThat(item.getLink()).isEqualTo("https://example.com/article");
            assertThat(item.getAuthor()).isEqualTo("u/testuser");
            assertThat(item.getRawContent()).contains("r/programming");
            assertThat(item.getRawContent()).contains("Score: 100");
        }
    }

    private void mockTokenResponse() {
        RedditTokenResponse tokenResponse = new RedditTokenResponse();
        tokenResponse.setAccessToken("test-access-token");
        tokenResponse.setExpiresIn(3600);
        tokenResponse.setTokenType("bearer");

        when(restTemplate.exchange(
                contains("access_token"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(RedditTokenResponse.class)
        )).thenReturn(new ResponseEntity<>(tokenResponse, HttpStatus.OK));
    }

    private void mockSubredditResponse(String subreddit, RedditPost... posts) {
        RedditListingResponse response = new RedditListingResponse();
        response.setKind("Listing");

        RedditListingData data = new RedditListingData();
        data.setChildren(java.util.Arrays.stream(posts)
                .map(post -> {
                    RedditPostWrapper wrapper = new RedditPostWrapper();
                    wrapper.setKind("t3");
                    wrapper.setData(post);
                    return wrapper;
                })
                .toList());

        response.setData(data);

        when(restTemplate.exchange(
                contains("/r/" + subreddit),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(RedditListingResponse.class)
        )).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));
    }

    private RedditPost createValidLinkPost() {
        RedditPost post = new RedditPost();
        post.setId("test123");
        post.setName("t3_test123");
        post.setTitle("Test Post");
        post.setAuthor("testauthor");
        post.setUrl("https://example.com/article");
        post.setPermalink("/r/programming/comments/test123/test_post/");
        post.setSubreddit("programming");
        post.setDomain("example.com");
        post.setSelf(false);
        post.setStickied(false);
        post.setOver18(false);
        post.setScore(42);
        post.setNumComments(10);
        post.setCreatedUtc(Instant.now().minusSeconds(3600).getEpochSecond()); // 1 hour ago
        return post;
    }

    private Source createSource(String url) {
        Source source = new Source();
        source.setId(UUID.randomUUID());
        source.setUrl(url);
        source.setType(SourceType.REDDIT);
        return source;
    }
}
