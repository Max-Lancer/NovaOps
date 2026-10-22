package com.novaops.backend.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AccessLogAsyncConfig {

  private static final Logger log = LoggerFactory.getLogger(AccessLogAsyncConfig.class);

  /**
   * 访问日志专用池，与智能体 / 知识库异步任务隔离。
   * 队列满时丢弃新日志，避免拒绝策略把写入打回请求线程。
   */
  @Bean(name = "accessLogExecutor")
  public ThreadPoolTaskExecutor accessLogExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("nova-access-log-");
    executor.setRejectedExecutionHandler((runnable, pool) -> log.warn("access log queue full, drop record"));
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(3);
    return executor;
  }
}
