package cz.mibcon.zazitky.common;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Routes of the React app (frontend/src/App.tsx) except "/". They get {@code app.html}, the page without
 * the prerendered landing page, so that the game does not flash the landing page before the app starts.
 * Search engines must not index them. Other paths without a file extension end with 404 and the same page,
 * the app then shows "Stránka nenalezena" ({@link ApiExceptionHandler}).
 */
@Controller
public class SpaPages {

    private static final Resource APP = new ClassPathResource("static/app.html");
    // A build without prerendering (backend tests) has only index.html
    private static final Resource INDEX = new ClassPathResource("static/index.html");

    @GetMapping({"/start", "/play", "/play/reward", "/play/finale", "/admin", "/admin/**"})
    ResponseEntity<Object> clientRoute() {
        return page(HttpStatus.OK);
    }

    static boolean isPagePath(String path) {
        return !path.startsWith("/api/") && !path.startsWith("/actuator/") && !path.contains(".");
    }

    static ResponseEntity<Object> page(HttpStatus status) {
        Resource page = APP.exists() ? APP : INDEX;
        if (!page.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noCache())
                .header("X-Robots-Tag", "noindex")
                .contentType(MediaType.TEXT_HTML)
                .body(page);
    }
}
