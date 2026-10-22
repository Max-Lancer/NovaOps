package com.novaops.backend.system.web;

import com.novaops.backend.system.service.ModuleHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ModuleHealthStartup implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ModuleHealthStartup.class);

  private final ModuleHealthService moduleHealthService;

  public ModuleHealthStartup(ModuleHealthService moduleHealthService) {
    this.moduleHealthService = moduleHealthService;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      moduleHealthService.checkAll();
    } catch (Exception exception) {
      log.warn("startup module health check failed", exception);
    }
  }
}
