package com.novaops.backend.ops.ticket.dto;

import org.springframework.core.io.Resource;

/** 附件下载载荷：原始文件名用于 Content-Disposition，resource 指向磁盘上的附件实体。 */
public class AttachmentDownload {

  private final String fileName;
  private final long size;
  private final Resource resource;

  public AttachmentDownload(String fileName, long size, Resource resource) {
    this.fileName = fileName;
    this.size = size;
    this.resource = resource;
  }

  public String getFileName() {
    return fileName;
  }

  public long getSize() {
    return size;
  }

  public Resource getResource() {
    return resource;
  }
}
