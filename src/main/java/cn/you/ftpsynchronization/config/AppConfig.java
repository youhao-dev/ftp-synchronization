package cn.you.ftpsynchronization.config;

import cn.you.ftpsynchronization.i18n.I18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 应用配置中心。兼容 application.properties/application.yml 与旧版配置文件，并把界面设置持久化到新配置文件。
 */
public class AppConfig {
    private static final Logger LOG=Logger.getLogger(AppConfig.class.getName());
    private static final String SETTINGS_NAME="ftp-synchronization-settings.properties";
    private static final String LEGACY_SETTINGS_NAME="ftp-upload-settings.properties";
    private final Path workingDirectory;
    private final Path settingsFile;
    private final Path legacySettingsFile;
    private final Properties properties=new Properties();

    /** 创建配置中心并加载兼容配置。 */
    public AppConfig() { this(Path.of("")); }

    /** 创建使用指定工作目录的配置中心，便于安全迁移和自动化测试。 */
    public AppConfig(Path workingDirectory) {
        this.workingDirectory=(workingDirectory==null?Path.of(""):workingDirectory).toAbsolutePath().normalize();
        this.settingsFile=this.workingDirectory.resolve(SETTINGS_NAME);
        this.legacySettingsFile=this.workingDirectory.resolve(LEGACY_SETTINGS_NAME);
        load();
    }

    /** 获取 FTP 地址。 */
    public synchronized String ftpHost() { return get("ftp.host","").trim(); }
    /** 获取 FTP 端口。 */
    public synchronized int ftpPort() { return getInt("ftp.port",21); }
    /** 获取 FTP 用户名。 */
    public synchronized String ftpUsername() { return get("ftp.username",""); }
    /** 获取 FTP 密码。 */
    public synchronized String ftpPassword() { return get("ftp.password",""); }
    /** 获取连接超时毫秒。 */
    public synchronized int connectTimeoutMs() { return clamp(getInt("ftp.connect-timeout-ms",10_000),1_000,120_000); }
    /** 获取控制连接读取超时毫秒。 */
    public synchronized int readTimeoutMs() { return clamp(getInt("ftp.read-timeout-ms",30_000),1_000,300_000); }
    /** 获取数据连接读取超时毫秒。 */
    public synchronized int dataTimeoutMs() { return clamp(getInt("ftp.data-timeout-ms",30_000),1_000,300_000); }
    /** 获取 FTP 连接池大小。 */
    public synchronized int ftpPoolSize() { return clamp(getInt("ftp.pool-size",3),1,16); }
    /** 获取等待连接池连接的最长时间。 */
    public synchronized int poolBorrowTimeoutMs() { return clamp(getInt("ftp.pool-borrow-timeout-ms",10_000),1_000,120_000); }
    /** 获取上传检查周期，单位秒。 */
    public synchronized int uploadIntervalSeconds() { return clamp(getInt("upload.interval-seconds",60),5,86_400); }
    /** 获取每分钟执行秒数，仅当周期为 60 秒时用于首次对齐。 */
    public synchronized int scheduleSecond() { return clamp(getInt("upload.schedule-second",58),0,59); }
    /** 获取每条规则检查的最新文件数量。 */
    public synchronized int latestFileCount() { return clamp(getInt("upload.latest-file-count",5),1,100); }
    /** 获取文件稳定时间，避免文件仍在写入时上传。 */
    public synchronized int fileStableSeconds() { return clamp(getInt("upload.file-stable-seconds",3),0,3_600); }
    /** 获取界面语言代码；auto 表示跟随系统。 */
    public synchronized String language() { return get("ui.language","auto").trim(); }
    /** 获取界面当前全部可编辑配置。 */
    public synchronized AppSettings snapshot() { return new AppSettings(ftpHost(),ftpPort(),ftpUsername(),ftpPassword(),connectTimeoutMs(),readTimeoutMs(),dataTimeoutMs(),ftpPoolSize(),poolBorrowTimeoutMs(),uploadIntervalSeconds(),scheduleSecond(),latestFileCount(),fileStableSeconds(),language()); }
    /** 获取界面配置文件位置。 */
    public Path settingsFile() { return settingsFile; }

    /**
     * 保存界面配置并立即更新内存值。
     */
    public synchronized void save(AppSettings settings) {
        validate(settings);
        set("ftp.host",settings.ftpHost().trim()); set("ftp.port",settings.ftpPort()); set("ftp.username",settings.ftpUsername()); set("ftp.password",settings.ftpPassword());
        set("ftp.connect-timeout-ms",settings.connectTimeoutMs()); set("ftp.read-timeout-ms",settings.readTimeoutMs()); set("ftp.data-timeout-ms",settings.dataTimeoutMs());
        set("ftp.pool-size",settings.ftpPoolSize()); set("ftp.pool-borrow-timeout-ms",settings.poolBorrowTimeoutMs());
        set("upload.interval-seconds",settings.uploadIntervalSeconds()); set("upload.schedule-second",settings.scheduleSecond()); set("upload.latest-file-count",settings.latestFileCount()); set("upload.file-stable-seconds",settings.fileStableSeconds());
        set("ui.language",settings.language());
        persistUiSettings();
    }

    /** 仅保存语言设置，供界面即时切换时使用。 */
    public synchronized void saveLanguage(String language) {
        set("ui.language",language==null||language.isBlank()?"auto":language);
        persistUiSettings();
    }

    /**
     * 按“内置默认 -> 工作目录配置 -> 界面持久化配置”的优先级加载，保证配置直接可用。
     */
    private void load() {
        loadClasspath("/application.yml",true); loadClasspath("/application.properties",false);
        loadFile(workingDirectory.resolve("application.yml"),true); loadFile(workingDirectory.resolve("application.yaml"),true); loadFile(workingDirectory.resolve("application.properties"),false);
        if (Files.isRegularFile(settingsFile)) loadFile(settingsFile,false);
        else if (Files.isRegularFile(legacySettingsFile)) {
            loadFile(legacySettingsFile,false);
            persistUiSettings();
            LOG.info("已迁移旧配置文件到 "+settingsFile);
        }
    }

    /** 加载 classpath 配置。 */
    private void loadClasspath(String name,boolean yaml) {
        try (InputStream input=AppConfig.class.getResourceAsStream(name)) { if (input!=null) { if (yaml) loadSimpleYaml(input); else properties.load(new InputStreamReader(input,StandardCharsets.UTF_8)); } }
        catch (Exception e) { LOG.log(Level.WARNING,"读取内置配置失败: "+name,e); }
    }

    /** 加载工作目录配置。 */
    private void loadFile(Path path,boolean yaml) {
        if (!Files.isRegularFile(path)) return;
        try (InputStream input=Files.newInputStream(path)) { if (yaml) loadSimpleYaml(input); else properties.load(new InputStreamReader(input,StandardCharsets.UTF_8)); }
        catch (Exception e) { LOG.log(Level.WARNING,"读取配置失败: "+path.toAbsolutePath(),e); }
    }

    /**
     * 解析本工具用到的简单 YAML 键值结构。
     */
    private void loadSimpleYaml(InputStream input) throws IOException {
        String section="";
        try (BufferedReader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
            String line;
            while ((line=reader.readLine())!=null) {
                String trimmed=line.trim();
                if (trimmed.isEmpty()||trimmed.startsWith("#")) continue;
                int indent=line.indexOf(trimmed);
                if (trimmed.endsWith(":")) { section=trimmed.substring(0,trimmed.length()-1).trim(); continue; }
                int idx=trimmed.indexOf(':'); if (idx<1) continue;
                String key=trimmed.substring(0,idx).trim(),value=stripQuotes(trimmed.substring(idx+1).trim());
                properties.setProperty(indent>0&&!section.isBlank()?section+"."+key:key,value);
            }
        }
    }

    /**
     * 仅保存界面负责的配置键，避免覆盖用户 application.properties 里的其它内容。
     */
    private void persistUiSettings() {
        Properties out=new Properties();
        String[] keys={"ftp.host","ftp.port","ftp.username","ftp.password","ftp.connect-timeout-ms","ftp.read-timeout-ms","ftp.data-timeout-ms","ftp.pool-size","ftp.pool-borrow-timeout-ms","upload.interval-seconds","upload.schedule-second","upload.latest-file-count","upload.file-stable-seconds","ui.language"};
        for (String key:keys) out.setProperty(key,get(key,""));
        Path absolute=settingsFile,parent=absolute.getParent(),temp=parent.resolve(absolute.getFileName()+".tmp");
        try {
            if (parent!=null) Files.createDirectories(parent);
            try (OutputStream output=Files.newOutputStream(temp)) { out.store(output,"FTP Synchronization UI settings"); }
            try { Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException atomicUnsupported) { Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new IllegalStateException(I18n.text("persistence.settingsFailed",absolute),e); }
    }

    /** 校验准备保存的配置。 */
    private void validate(AppSettings s) {
        if (s==null) throw new IllegalArgumentException(I18n.text("validation.settingsRequired"));
        if (s.ftpHost()==null||s.ftpHost().isBlank()) throw new IllegalArgumentException(I18n.text("validation.ftpHost"));
        if (s.ftpPort()<1||s.ftpPort()>65535) throw new IllegalArgumentException(I18n.text("validation.range",I18n.text("field.ftpPort"),1,65535));
        if (s.connectTimeoutMs()<1_000||s.readTimeoutMs()<1_000||s.dataTimeoutMs()<1_000) throw new IllegalArgumentException(I18n.text("validation.range",I18n.text("field.connectTimeout"),1000,300000));
        if (s.ftpPoolSize()<1||s.ftpPoolSize()>16) throw new IllegalArgumentException(I18n.text("validation.range",I18n.text("field.poolSize"),1,16));
        if (s.poolBorrowTimeoutMs()<1_000) throw new IllegalArgumentException(I18n.text("validation.range",I18n.text("field.poolWait"),1000,120000));
        if (s.uploadIntervalSeconds()<5||s.uploadIntervalSeconds()>86_400) throw new IllegalArgumentException(I18n.text("validation.frequency"));
        if (s.scheduleSecond()<0||s.scheduleSecond()>59) throw new IllegalArgumentException(I18n.text("validation.range",I18n.text("field.alignSecond"),0,59));
        if (s.latestFileCount()<1||s.latestFileCount()>100) throw new IllegalArgumentException(I18n.text("validation.range",I18n.text("field.defaultCount"),1,100));
        if (s.fileStableSeconds()<0||s.fileStableSeconds()>3_600) throw new IllegalArgumentException(I18n.text("validation.range",I18n.text("field.stableSeconds"),0,3600));
    }

    /** 去掉 YAML 字符串两侧引号。 */
    private String stripQuotes(String value) { return value.length()>=2&&((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'")))?value.substring(1,value.length()-1):value; }
    /** 读取字符串配置。 */
    private String get(String key,String defaultValue) { return properties.getProperty(key,defaultValue); }
    /** 设置字符串配置。 */
    private void set(String key,Object value) { properties.setProperty(key,String.valueOf(value==null?"":value)); }
    /** 读取整数配置，非法值回退默认值。 */
    private int getInt(String key,int defaultValue) { try { return Integer.parseInt(get(key,String.valueOf(defaultValue)).trim()); } catch (NumberFormatException e) { return defaultValue; } }
    /** 把整数限制在指定范围。 */
    private int clamp(int value,int min,int max) { return Math.max(min,Math.min(max,value)); }
}
