package dev.cctasks.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import dev.cctasks.task.TaskRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * HTTP(読み取り)経路のウォームアップ。{@link WriteWarmup} の読み取り版。
 *
 * 低速なディスク(アイドル時に停止するものを含む)を持つ環境では初回アクセスが
 * 極端に重くなる。原因は二重取りで、
 * (1) DispatcherServlet・セキュリティフィルタ・MVC のクラスロードと JIT が
 *     初回リクエストに乗る(ローカル実測でも初回は 40 倍遅い)
 * (2) そのクラスロードや静的資材の読み出しが jar からの遅延読み込みのため、
 *     眠っていたディスクを直撃して読み出し待ち(数秒〜十数秒)になる
 *
 * 対策も二段構え:
 * - 起動直後に自分自身へ HTTP リクエストを投げ、クラスロード・JIT・jar 読みの
 *   コストをデプロイ時に前払いする(クラスは一度載れば JVM が持ち続ける)
 * - 静的資材と SQLite は放置するとページキャッシュから追い出され、久しぶりの
 *   アクセスがまたディスクを直撃する。定期的に触れてキャッシュに留める
 *   (キャッシュヒットならディスク I/O がゼロになるので、遅いディスクでも待たされない)
 */
@Component
public class HttpWarmup {

    private static final Logger log = LoggerFactory.getLogger(HttpWarmup.class);

    private final TaskRepository taskRepository;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile int port = -1;

    public HttpWarmup(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @EventListener(WebServerInitializedEvent.class)
    public void onWebServerInitialized(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long started = System.nanoTime();
        // 2 周目で JIT の効きを確認しつつ、1 周目のクラスロード漏れも拾う
        for (int i = 0; i < 2; i++) {
            request("/");
            request("/api/tasks"); // 本番は 401 だが、フィルタチェーンと MVC 経路は通る
        }
        keepWarm();
        log.info("HTTP 経路をウォームアップしました ({} ms)",
                (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * 静的資材(jar 内)と SQLite をページキャッシュに留める。
     * 触るのをやめると数時間後の「久しぶりの 1 発目」がディスク直撃に戻る。
     */
    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void keepWarm() {
        touchStaticResources();
        touchDatabase();
    }

    private void request(String path) {
        if (port <= 0) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            client.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("ウォームアップリクエストに失敗しました: {}", path, e);
        }
    }

    /** classpath:/static 配下(SPA の JS/CSS/アイコン等)を全部読み、jar のページをキャッシュに載せる。 */
    private void touchStaticResources() {
        try {
            var resolver = new PathMatchingResourcePatternResolver();
            for (Resource resource : resolver.getResources("classpath:/static/**")) {
                if (!resource.isReadable()) {
                    continue;
                }
                // ディレクトリエントリ等で個別に失敗しても残りは読み続ける
                try (var in = resource.getInputStream()) {
                    in.readAllBytes();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.warn("静的資材のウォームアップに失敗しました", e);
        }
    }

    private void touchDatabase() {
        try {
            taskRepository.count();
        } catch (Exception e) {
            log.warn("DB のウォームアップに失敗しました", e);
        }
    }
}
