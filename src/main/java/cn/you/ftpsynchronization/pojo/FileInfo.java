package cn.you.ftpsynchronization.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * FTP 文件传输规则。
 */
public class FileInfo {
    public static final String TRANSFER_UPLOAD="UPLOAD";
    public static final String TRANSFER_DOWNLOAD="DOWNLOAD";

    private String id;
    private String filename;
    private String basePath;
    private String rulePath;
    private String remoteBasePath;
    private String name;
    private long delayTime;
    private Boolean isRunning=true;
    private Integer templateVersion;
    private Integer latestFileCount;
    //传输方向：UPLOAD 上传，DOWNLOAD 下载；旧配置缺失时默认上传
    private String transferType=TRANSFER_UPLOAD;

    /** 获取规则ID。 */
    public String getId() { return id; }
    /** 设置规则ID。 */
    public FileInfo setId(String id) { this.id=id; return this; }
    /** 获取文件名称或说明。 */
    public String getFilename() { return filename; }
    /** 设置文件名称或说明。 */
    public FileInfo setFilename(String filename) { this.filename=filename; return this; }
    /** 获取本地根目录。 */
    public String getBasePath() { return basePath; }
    /** 设置本地根目录。 */
    public FileInfo setBasePath(String basePath) { this.basePath=basePath; return this; }
    /** 获取动态规则路径。 */
    public String getRulePath() { return rulePath; }
    /** 设置动态规则路径。 */
    public FileInfo setRulePath(String rulePath) { this.rulePath=rulePath; return this; }
    /** 获取FTP远程根目录。 */
    public String getRemoteBasePath() { return remoteBasePath; }
    /** 设置FTP远程根目录。 */
    public FileInfo setRemoteBasePath(String remoteBasePath) { this.remoteBasePath=remoteBasePath; return this; }
    /** 获取规则名称。 */
    public String getName() { return name; }
    /** 设置规则名称。 */
    public FileInfo setName(String name) { this.name=name; return this; }
    /** 获取时间偏移分钟数。 */
    public long getDelayTime() { return delayTime; }
    /** 设置时间偏移分钟数。 */
    public FileInfo setDelayTime(long delayTime) { this.delayTime=delayTime; return this; }
    /** 获取运行状态；旧配置缺失该值时按启用处理。 */
    public boolean getIsRunning() { return isRunning==null||isRunning; }
    /** 设置运行状态。 */
    public FileInfo setIsRunning(boolean isRunning) { this.isRunning=isRunning; return this; }
    /** 获取模板版本。 */
    public Integer getTemplateVersion() { return templateVersion; }
    /** 设置模板版本。 */
    public FileInfo setTemplateVersion(Integer templateVersion) { this.templateVersion=templateVersion; return this; }
    /** 获取单次检查最新文件数量。 */
    public Integer getLatestFileCount() { return latestFileCount; }
    /** 设置单次检查最新文件数量。 */
    public FileInfo setLatestFileCount(Integer latestFileCount) { this.latestFileCount=latestFileCount; return this; }
    /** 获取传输方向；旧配置没有该字段时默认返回UPLOAD。 */
    public String getTransferType() { return TRANSFER_DOWNLOAD.equalsIgnoreCase(transferType)?TRANSFER_DOWNLOAD:TRANSFER_UPLOAD; }
    /** 设置传输方向，非法或空值按UPLOAD处理。 */
    public FileInfo setTransferType(String transferType) { this.transferType=TRANSFER_DOWNLOAD.equalsIgnoreCase(transferType)?TRANSFER_DOWNLOAD:TRANSFER_UPLOAD; return this; }
    /** 判断是否使用旧版补零模板规则。 */
    @JsonIgnore public boolean usesLegacyTemplate() { return templateVersion==null||templateVersion<=1; }
    /** 判断当前是否为下载规则。 */
    @JsonIgnore public boolean isDownload() { return TRANSFER_DOWNLOAD.equals(getTransferType()); }
    /** 判断当前是否为上传规则。 */
    @JsonIgnore public boolean isUpload() { return !isDownload(); }
}
