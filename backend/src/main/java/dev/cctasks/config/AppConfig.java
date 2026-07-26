package dev.cctasks.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling は HttpWarmup の定期ウォームアップ用
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AppConfig {

    /** タイムスタンプは常に UTC。テストで差し替えられるよう Bean にしておく。 */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
