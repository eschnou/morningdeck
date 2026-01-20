package be.transcode.morningdeck.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to handle Private Network Access (PNA) requests.
 * This allows public websites (like lovableproject.com) to access localhost during development.
 *
 * @see <a href="https://developer.chrome.com/blog/private-network-access-preflight/">Private Network Access</a>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PrivateNetworkAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Check if this is a preflight request with Private Network Access header
        String privateNetworkHeader = request.getHeader("Access-Control-Request-Private-Network");

        if (privateNetworkHeader != null) {
            // Allow private network access
            response.setHeader("Access-Control-Allow-Private-Network", "true");
        }

        filterChain.doFilter(request, response);
    }
}
