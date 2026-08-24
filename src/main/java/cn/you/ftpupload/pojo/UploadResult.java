package cn.you.ftpupload.pojo;

/**
 * 单条规则同步结果。
 */
public record UploadResult(int uploaded,int skipped,int failed) {}
