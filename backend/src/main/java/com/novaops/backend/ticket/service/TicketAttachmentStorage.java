package com.novaops.backend.ticket.service;

import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.ticket.config.TicketProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 工单附件落盘：磁盘名用附件 id（原始文件名只入库用于展示与下载头），
 * 因此重命名展示不受文件名里的非法字符影响；写入走临时文件 + 原子改名，失败不留半截文件。
 * 附件不做扩展名白名单（工单可能挂日志、截图、压缩包），只拦截空文件、超限与路径穿越。
 */
@Service
public class TicketAttachmentStorage {

  private final TicketProperties properties;

  public TicketAttachmentStorage(TicketProperties properties) {
    this.properties = properties;
  }

  public record StoredAttachment(Path path, String fileName, long size) {}

  public StoredAttachment save(String ticketId, String attachmentId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(400, "附件不能为空");
    }
    if (file.getSize() > properties.getMaxAttachmentSizeBytes()) {
      throw new BusinessException(400, "附件超过允许的大小上限");
    }
    String fileName = safeName(file.getOriginalFilename());
    Path temporary = null;
    try {
      Path directory = directory(ticketId);
      Files.createDirectories(directory);
      Path target = directory.resolve(attachmentId);
      temporary = directory.resolve(attachmentId + ".tmp");
      try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(temporary)) {
        input.transferTo(output);
      }
      commit(temporary, target);
      temporary = null;
      return new StoredAttachment(target, fileName, file.getSize());
    } catch (IOException ex) {
      throw new BusinessException(500, "附件保存失败");
    } finally {
      deleteQuietly(temporary);
    }
  }

  /** 解析并校验附件实体路径，避免 attachmentId 里夹带 ../ 跳出附件根目录。 */
  public Path resolve(String ticketId, String attachmentId) {
    Path directory = directory(ticketId);
    Path path = directory.resolve(attachmentId).normalize();
    if (!path.startsWith(directory)) {
      throw new BusinessException(400, "非法附件路径");
    }
    return path;
  }

  public void delete(Path path) {
    deleteQuietly(path);
  }

  private Path directory(String ticketId) {
    Path root = Path.of(properties.getAttachmentStoragePath()).toAbsolutePath().normalize();
    Path directory = root.resolve(ticketId).normalize();
    if (!directory.startsWith(root)) {
      throw new BusinessException(400, "非法存储路径");
    }
    return directory;
  }

  /** 原始文件名只用于展示与下载头：剥掉任何路径片段，并限制在列宽允许的长度内。 */
  private static String safeName(String raw) {
    String name = raw == null || raw.isBlank() ? "attachment" : raw;
    int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    if (name.isBlank()) {
      name = "attachment";
    }
    return name.length() > 255 ? name.substring(name.length() - 255) : name;
  }

  private static void commit(Path temporary, Path target) throws IOException {
    Files.deleteIfExists(target);
    try {
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
    }
  }
}
