package com.duanju.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * <p>使用专用的 HLS 转码线程池,而不是 Spring 默认的 SimpleAsyncTaskExecutor:</p>
 * <ul>
 *   <li>FFmpeg 是 CPU 密集型,线程数不能太多,否则互相抢占 CPU 反而更慢</li>
 *   <li>有界队列 + CallerRunsPolicy 拒绝策略,防止批量上传时瞬间打爆内存</li>
 *   <li>线程名带 hls-transcode- 前缀,便于排查问题</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${duanju.hls.transcode.core-pool-size:2}")
    private int corePoolSize;

    @Value("${duanju.hls.transcode.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${duanju.hls.transcode.queue-capacity:50}")
    private int queueCapacity;

    @Bean("hlsTranscodeExecutor")
    public Executor hlsTranscodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("hls-transcode-");
        // 队列满且达到 maxPoolSize 时,由调用线程自己执行(反压机制)
        // 这样既不会丢任务,也不会无限制创建线程
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        log.info("HLS转码线程池初始化: core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
