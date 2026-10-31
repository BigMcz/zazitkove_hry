package cz.mibcon.zazitky.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the frontend build packaged in {@code classpath:/static/}; "/" is {@code index.html} with the
 * prerendered landing page (Spring Boot's welcome page). Routes of the React app are in {@link SpaPages}.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    private static final Map<String, String> ETAGS = new ConcurrentHashMap<>();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite puts a content hash into the file names, so they never change.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // Files in the JAR all have the same date (reproducible build, 1980), so Last-Modified would answer 304 to the
        // old index.html after every deployment; the browser revalidates by an ETag of the content instead.
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .setUseLastModified(false)
                .setEtagGenerator(SpaWebConfig::contentEtag);
    }

    /** MD5 of the file, computed once: the files in the JAR do not change while the application runs. */
    static String contentEtag(Resource resource) {
        return ETAGS.computeIfAbsent(resource.getDescription(), key -> {
            try (InputStream content = resource.getInputStream()) {
                return "\"" + DigestUtils.md5DigestAsHex(content) + "\"";
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
