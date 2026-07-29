package dev.cctasks.config;

import java.io.IOException;
import java.time.Duration;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Vue SPA を Spring の static から配信する。
 * 実ファイルが無いパス(SPA のルーティング先)は index.html にフォールバックさせる。
 * ただし /api は素通しして本来のハンドラ・404 に任せる。
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

    private static final ClassPathResource INDEX = new ClassPathResource("static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite の /assets はファイル名にコンテンツハッシュが入るため長期キャッシュしてよい。
        // ここに Cache-Control を付けないと毎回取り直しになり、初回以外のアクセスも重くなる
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable());
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
                        if (resourcePath.startsWith("api/")) {
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
     *
     * <p>Boot 3 までは ContentNegotiationConfigurer の mediaType 登録で静的配信にも
     * 効いていたが、Boot 4 (Framework 7) では効かなくなったため、
     * コンテナの MIME マッピングに直接足す。
     */
    @Bean
    WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webmanifestMimeMapping() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
