package com.novaops.backend;

import com.novaops.backend.agent.mcp.McpRemoteProperties;
import com.novaops.backend.common.config.SecurityProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = {
    "com.novaops.backend.auth.mapper",
    "com.novaops.backend.ops.ticket.mapper",
    "com.novaops.backend.kb.mapper",
    "com.novaops.backend.agent.mapper",
    "com.novaops.backend.agent.task.mapper",
    "com.novaops.backend.ops.asset.mapper",
    "com.novaops.backend.ops.dashboard.mapper"
})
@EnableConfigurationProperties({SecurityProperties.class, McpRemoteProperties.class})
@EnableAsync
@EnableScheduling
public class NovaOpsBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(NovaOpsBackendApplication.class, args);
  }
}
