package com.novaops.backend.system.dto;

import java.util.ArrayList;
import java.util.List;

public class ModuleHealthResponse {

  private List<ModuleCheckItemResponse> latest = new ArrayList<>();
  private List<ModuleCheckItemResponse> history = new ArrayList<>();

  public List<ModuleCheckItemResponse> getLatest() {
    return latest;
  }

  public void setLatest(List<ModuleCheckItemResponse> latest) {
    this.latest = latest;
  }

  public List<ModuleCheckItemResponse> getHistory() {
    return history;
  }

  public void setHistory(List<ModuleCheckItemResponse> history) {
    this.history = history;
  }
}
