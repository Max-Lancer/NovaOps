package com.novaops.backend.kb.service;

import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.kb.config.KbProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KbFileStorage {
  private static final Set<String> ALLOWED = Set.of("md","pdf","doc","docx");
  private static final int BUFFER_SIZE = 8192;
  private final KbProperties properties;
  public KbFileStorage(KbProperties properties){this.properties=properties;}

  /** 落盘结果：正式路径、内容 SHA-256（十六进制）、扩展名。 */
  public record StoredFile(Path path,String contentHash,String extension){}

  public String extension(String fileName){
    int dot=fileName==null?-1:fileName.lastIndexOf('.');
    String extension=dot<0?"":fileName.substring(dot+1).toLowerCase(Locale.ROOT);
    if(!ALLOWED.contains(extension)) throw new BusinessException(400,"仅支持 md/pdf/doc/docx 文件");
    return extension;
  }

  /**
   * 一次流式读取同时完成内容 SHA-256 与写同目录临时文件，再原子替换到 {@code source-{摘要}.{ext}}。
   * 文件名带内容摘要，重复落盘同一内容不会产生第二个文件；中途失败只残留临时文件并由 finally 清理，
   * 正式文件要么是上一版、要么是完整新版，不会出现半截文件。
   */
  public StoredFile save(String documentId,MultipartFile file){
    if(file.isEmpty()) throw new BusinessException(400,"上传文件不能为空");
    if(file.getSize()>properties.getMaxFileSizeBytes()) throw new BusinessException(400,"文件超过允许的大小上限");
    String ext=extension(file.getOriginalFilename());
    Path temporary=temporaryPath(documentId);
    String hash;
    try(InputStream input=file.getInputStream(); OutputStream output=Files.newOutputStream(temporary)){
      hash=digest(input,output);
    }catch(IOException ex){
      deleteQuietly(temporary);
      throw new BusinessException(500,"文件保存失败");
    }
    return commitTemporary(documentId,ext,temporary,hash);
  }

  /** 在 documentId 目录下申请临时文件路径（与正式文件同目录，保证可以原子改名）。分片合并也复用这里。 */
  public Path temporaryPath(String documentId){
    try {
      Path directory=directory(documentId);
      Files.createDirectories(directory);
      return directory.resolve("source.tmp");
    } catch(IOException ex){
      throw new BusinessException(500,"文件保存失败");
    }
  }

  /** 把写好的临时文件提交为正式文件：目标名由内容摘要派生，改名是原子的。 */
  public StoredFile commitTemporary(String documentId,String extension,Path temporary,String contentHash){
    try {
      Path target=directory(documentId).resolve("source-"+contentHash.substring(0,16)+"."+extension);
      commit(temporary,target);
      return new StoredFile(target,contentHash,extension);
    } catch(IOException ex){
      deleteQuietly(temporary);
      throw new BusinessException(500,"文件保存失败");
    }
  }

  public void delete(Path path){deleteQuietly(path);}

  static MessageDigest sha256(){
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch(NoSuchAlgorithmException ex){
      throw new IllegalStateException("SHA-256 不可用",ex);
    }
  }

  /** 边写边更新摘要，用于单流或多流拼接。 */
  static void transfer(InputStream input,OutputStream output,MessageDigest digest)throws IOException{
    byte[] buffer=new byte[BUFFER_SIZE];
    int read;
    while((read=input.read(buffer))>0){
      digest.update(buffer,0,read);
      output.write(buffer,0,read);
    }
  }

  static String digest(InputStream input,OutputStream output)throws IOException{
    return HexFormat.of().formatHex(digestBytes(input,output));
  }

  private static byte[] digestBytes(InputStream input,OutputStream output)throws IOException{
    MessageDigest digest=sha256();
    transfer(input,output,digest);
    return digest.digest();
  }

  static void commit(Path temporary,Path target)throws IOException{
    Files.deleteIfExists(target);
    try {
      Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);
    } catch(AtomicMoveNotSupportedException ex){
      Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
    }
  }

  static void deleteQuietly(Path path){
    if(path==null) return;
    try{Files.deleteIfExists(path);}catch(IOException ignored){}
  }

  private Path directory(String documentId){
    Path root=Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
    Path directory=root.resolve(documentId).normalize();
    if(!directory.startsWith(root)) throw new BusinessException(400,"非法存储路径");
    return directory;
  }
}
