package cn.you.ftpupload.pojo;

import java.time.LocalDateTime;

/**
 * 系统任务记录，保存在内存中。
 */
public record SystemLogEntry(LocalDateTime time,String level,String message) {}
