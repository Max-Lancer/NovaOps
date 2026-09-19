package com.novaops.backend.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ticket")
public class TicketProperties {
  private String attachmentStoragePath = "./data/attachments";
  private long maxAttachmentSizeBytes = 20_971_520;

  public String getAttachmentStoragePath() {
    return attachmentStoragePath;
  }

  public void setAttachmentStoragePath(String v) {
    attachmentStoragePath = v;
  }

  public long getMaxAttachmentSizeBytes() {
    return maxAttachmentSizeBytes;
  }

  public void setMaxAttachmentSizeBytes(long v) {
    maxAttachmentSizeBytes = v;
  }
}
