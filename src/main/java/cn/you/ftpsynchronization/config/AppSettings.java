package cn.you.ftpsynchronization.config;

/**
 * 可由界面修改并持久化的运行配置。
 */
public record AppSettings(String ftpHost,int ftpPort,String ftpUsername,String ftpPassword,int connectTimeoutMs,int readTimeoutMs,int dataTimeoutMs,int ftpPoolSize,int poolBorrowTimeoutMs,int uploadIntervalSeconds,int scheduleSecond,int latestFileCount,int fileStableSeconds,String language) {}
