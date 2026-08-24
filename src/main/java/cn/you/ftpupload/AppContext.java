package cn.you.ftpupload;

import cn.you.ftpupload.config.AppConfig;
import cn.you.ftpupload.log.InMemoryLogStore;
import cn.you.ftpupload.service.CopyFile;
import cn.you.ftpupload.service.PathRules;
import cn.you.ftpupload.service.PathRulesService;
import cn.you.ftpupload.service.impl.CopyFileImpl;
import cn.you.ftpupload.service.impl.PathRulesServiceImpl;
import cn.you.ftpupload.stack.FtpUploadStack;
import cn.you.ftpupload.utils.FtpConnectionPool;
import cn.you.ftpupload.utils.FtpUtil;

/**
 * 纯 JavaFX 版本的轻量对象容器，集中管理配置、规则、日志、FTP 连接池和调度器生命周期。
 */
public final class AppContext {
    private static final AppContext INSTANCE=new AppContext();
    private final AppConfig config=new AppConfig();
    private final InMemoryLogStore logStore=new InMemoryLogStore();
    private final PathRules pathRules=new PathRules();
    private final PathRulesService pathRulesService=new PathRulesServiceImpl(pathRules);
    private final FtpConnectionPool ftpPool=new FtpConnectionPool(config);
    private final FtpUtil ftpUtil=new FtpUtil(ftpPool,logStore);
    private final CopyFile copyFile=new CopyFileImpl(ftpUtil);
    private final FtpUploadStack uploadStack=new FtpUploadStack(pathRulesService,copyFile,config,logStore);

    /** 初始化应用内各核心组件。 */
    private AppContext() {}

    /** 获取应用全局上下文。 */
    public static AppContext getInstance() { return INSTANCE; }
    /** 获取应用配置。 */
    public AppConfig config() { return config; }
    /** 获取规则服务。 */
    public PathRulesService pathRulesService() { return pathRulesService; }
    /** 获取内存日志中心。 */
    public InMemoryLogStore logStore() { return logStore; }
    /** 获取 FTP 连接池。 */
    public FtpConnectionPool ftpPool() { return ftpPool; }
    /** 获取 FTP 工具。 */
    public FtpUtil ftpUtil() { return ftpUtil; }
    /** 获取上传调度器。 */
    public FtpUploadStack uploadStack() { return uploadStack; }

    /**
     * 应用配置保存后刷新依赖配置的运行组件。
     */
    public void settingsChanged() { ftpPool.reconfigure(); uploadStack.reschedule(); logStore.info("运行配置已更新，FTP 连接池与上传计划已刷新"); }

    /**
     * 关闭后台资源。
     */
    public void shutdown() { uploadStack.shutdown(); ftpPool.close(); }
}
