package dev.cctasks.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import org.springframework.stereotype.Component;

/**
 * IP あたりのトークンバケット。ログイン試行と MCP 認証の乱打を抑える(仕様書 §6.3)。
 * 自分専用アプリなので分散対応は不要、プロセス内メモリで十分。
 */
@Component
public class IpRateLimiter {

    /** 単独利用なので上限を設けても実害は無いが、無制限に増やさないための上限。 */
    private static final int MAX_TRACKED_IPS = 10_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public IpRateLimiter(CcTasksProperties properties) {
        this.requestsPerMinute = properties.rateLimit().requestsPerMinute();
    }

    /** 消費できたら true、枯渇していたら false。 */
    public boolean tryConsume(String key) {
        if (buckets.size() > MAX_TRACKED_IPS) {
            buckets.clear();
        }
        return buckets.computeIfAbsent(key, k -> newBucket()).tryConsume(1);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
