package com.flatmeals.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves the React website from the same server as the API.
 *
 * "npm run build" turns the frontend into plain files in frontend/dist.
 * build.ps1 (or the Dockerfile) copies them into src/main/resources/static,
 * so they end up inside the jar. This class decides how they are served:
 *
 *   /assets/app-3f9a1c.js    a real file: sent as-is. Vite puts a fingerprint in
 *                            the name, so browsers may keep it for a year.
 *   /m/2026/10/meals         not a file, but a page of the React app. We send
 *                            index.html and React shows the right page. Without
 *                            this, refreshing the browser on that page gives 404.
 *   /api/...                 never handled here. Unknown API URLs stay a real 404.
 *   /something.png           has a dot, so it was meant to be a file; if it's
 *                            missing, that is a real 404 too.
 *
 * index.html itself is sent with "no-cache", so after a new deploy every
 * browser picks up the new version straight away.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String LOCATION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(LOCATION + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());

        registry.addResourceHandler("/**")
                .addResourceLocations(LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable() && checkResource(file, location)) {
                            return file;
                        }
                        return isAppPage(path) ? indexHtml(location) : null;
                    }
                });
    }

    /** A path the React router handles: not the API and not a file name. */
    static boolean isAppPage(String path) {
        if (path.equals("api") || path.startsWith("api/")) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }

    private static Resource indexHtml(Resource location) throws IOException {
        Resource index = location.createRelative("index.html");
        return index.exists() && index.isReadable() ? index : null;
    }
}
