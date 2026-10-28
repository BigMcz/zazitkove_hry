package cz.mibcon.zazitky.common;

import java.io.IOException;
import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the frontend build packaged in {@code classpath:/static/}. Client-side routes of the SPA
 * ({@code /games/…}, {@code /play/…}) fall back to {@code index.html}; API, actuator and missing
 * files still end with 404.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite puts a content hash into the file names, so they never change.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(STATIC + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    static class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (!resourcePath.isEmpty() && !resourcePath.endsWith("/")) {
                Resource file = super.getResource(resourcePath, location);
                if (file != null) {
                    return file;
                }
                if (!isClientRoute(resourcePath)) {
                    return null;
                }
            }
            return super.getResource("index.html", location);
        }

        private static boolean isClientRoute(String path) {
            return !path.startsWith("api/") && !path.startsWith("actuator/") && !path.contains(".");
        }
    }
}
