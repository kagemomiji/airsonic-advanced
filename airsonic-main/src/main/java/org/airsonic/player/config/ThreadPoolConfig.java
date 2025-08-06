package org.airsonic.player.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "BroadcastThreadPool")
    @Lazy
    public ThreadPoolTaskExecutor configThreadPool() {
        var threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(2);
        threadPool.setMaxPoolSize(5);
        threadPool.setQueueCapacity(500);
        threadPool.setDaemon(true);
        threadPool.setThreadNamePrefix("BroadcastThread-");
        return threadPool;
    }

    @Bean(name = "PodcastDownloadThreadPool")
    @Lazy
    public ThreadPoolTaskExecutor podcastDownloadThreadPool() {
        var threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(2);
        threadPool.setMaxPoolSize(3);
        threadPool.setQueueCapacity(500);
        threadPool.setDaemon(true);
        threadPool.setThreadNamePrefix("podcast-download");
        return threadPool;
    }

    @Bean(name = "PodcastRefreshThreadPool")
    @Lazy
    public ThreadPoolTaskExecutor podcastRefreshThreadPool() {
        var threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(2);
        threadPool.setMaxPoolSize(5);
        threadPool.setQueueCapacity(1000);
        threadPool.setDaemon(true);
        threadPool.setThreadNamePrefix("podcast-refresh");
        return threadPool;
    }
}
