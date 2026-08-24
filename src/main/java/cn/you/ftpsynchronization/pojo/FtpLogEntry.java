package cn.you.ftpsynchronization.pojo;

import java.time.LocalDateTime;

/**
 * 单文件 FTP 处理记录，保存在内存中。
 */
public record FtpLogEntry(LocalDateTime time,String ruleName,String fileName,String localPath,String remotePath,TransferStatus status,long fileSize,long durationMs,LocalizedMessage message) {}
