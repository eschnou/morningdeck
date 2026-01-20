package be.transcode.morningdeck.server.provider.sourcefetch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility for normalizing URLs (for GUID generation) and resolving relative URLs.
 */
public final class UrlNormalizer {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "fbclid", "gclid", "msclkid", "mc_cid", "mc_eid"
    );

    private UrlNormalizer() {
    }

    /**
     * Normalizes a URL for use as a GUID:
     * - Lowercase hostname
     * - Remove trailing slash from path
     * - Remove common tracking parameters (utm_*, ref, fbclid, etc.)
     * - Preserve path and non-tracking query params
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        try {
            URI uri = new URI(url.trim());

            // Lowercase the host
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase();
            }

            // Remove trailing slash from path
            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            // Remove tracking params from query
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                query = removeTrackingParams(query);
                if (query.isEmpty()) {
                    query = null;
                }
            }

            // Reconstruct URI
            URI normalized = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    path,
                    query,
                    uri.getFragment()
            );

            return normalized.toString();
        } catch (URISyntaxException e) {
            // If parsing fails, return original URL
            return url.trim();
        }
    }

    /**
     * Resolves a potentially relative URL against a base URL.
     */
    public static String resolveRelative(String baseUrl, String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isBlank()) {
            return relativeUrl;
        }

        // Already absolute
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }

        // Protocol-relative URL
        if (relativeUrl.startsWith("//")) {
            try {
                URI baseUri = new URI(baseUrl);
                return baseUri.getScheme() + ":" + relativeUrl;
            } catch (URISyntaxException e) {
                return "https:" + relativeUrl;
            }
        }

        try {
            URI base = new URI(baseUrl);
            URI resolved = base.resolve(relativeUrl);
            return resolved.toString();
        } catch (URISyntaxException e) {
            // If resolution fails, return the relative URL as-is
            return relativeUrl;
        }
    }

    private static String removeTrackingParams(String query) {
        return Arrays.stream(query.split("&"))
                .filter(param -> {
                    String paramName = param.contains("=") ? param.substring(0, param.indexOf('=')) : param;
                    return !TRACKING_PARAMS.contains(paramName.toLowerCase());
                })
                .collect(Collectors.joining("&"));
    }
}
