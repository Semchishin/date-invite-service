package semchishin.dateinviteservice.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Set<String> STATIC_EXTENSIONS = Set.of(
            ".css", ".js", ".svg", ".png", ".jpg", ".jpeg", ".webp", ".ico", ".woff", ".woff2", ".map");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isStatic(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("{} {} -> {} за {} мс", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), millis);
            MDC.remove(MDC_KEY);
        }
    }

    private static boolean isStatic(String uri) {
        int dot = uri.lastIndexOf('.');
        int slash = uri.lastIndexOf('/');
        return dot > slash && STATIC_EXTENSIONS.contains(uri.substring(dot).toLowerCase());
    }
}
