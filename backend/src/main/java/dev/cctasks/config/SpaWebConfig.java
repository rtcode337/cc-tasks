package dev.cctasks.config;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Vue SPA を Spring の static から配信する。
 * 実ファイルが無いパス(SPA のルーティング先)は index.html にフォールバックさせる。
 * ただし /api と /mcp は素通しして本来のハンドラ・404 に任せる。
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("mcp")) {
                            return null;
                        }
                        // 拡張子付き(= 資材の取得)で実体が無いなら素直に 404。
                        // ここで index.html を返すと、消えた JS を取りに来た古い
                        // Service Worker に HTML が渡って謎のエラーになる
                        if (looksLikeAsset(resourcePath)) {
                            return null;
                        }
                        // フロントを同梱していない(バックエンド単体)ときは 404 のままにする
                        return INDEX.exists() ? INDEX : null;
                    }
                });
    }

    /** 最後のセグメントに拡張子があるか。SPA のルート("/tasks/12")には無い。 */
    private static boolean looksLikeAsset(String resourcePath) {
        int lastSlash = resourcePath.lastIndexOf('/');
        return resourcePath.indexOf('.', lastSlash + 1) >= 0;
    }

    /**
     * Tomcat の既定に .webmanifest が無く、octet-stream で返ってしまう。
     * それだとブラウザが manifest として読まない。
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("webmanifest", MediaType.parseMediaType("application/manifest+json"));
    }
}
