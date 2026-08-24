package cn.you.ftpsynchronization;

import cn.you.ftpsynchronization.config.AppConfig;
import cn.you.ftpsynchronization.i18n.I18n;
import cn.you.ftpsynchronization.log.InMemoryLogStore;
import cn.you.ftpsynchronization.pojo.LocalizedMessage;
import cn.you.ftpsynchronization.service.CopyFile;
import cn.you.ftpsynchronization.service.PathRules;
import cn.you.ftpsynchronization.service.PathRulesService;
import cn.you.ftpsynchronization.service.impl.CopyFileImpl;
import cn.you.ftpsynchronization.service.impl.PathRulesServiceImpl;
import cn.you.ftpsynchronization.stack.FtpSynchronizationStack;
import cn.you.ftpsynchronization.utils.FtpConnectionPool;
import cn.you.ftpsynchronization.utils.FtpUtil;

/**
 * 纯 JavaFX 版本的轻量对象容器，集中管理配置、规则、日志、FTP 连接池和调度器生命周期。
 */
public final class AppContext {
    private static final AppContext INSTANCE=new AppContext();
    private final AppConfig config=new AppConfig();
    private final InMemoryLogStore logStore;
    private final PathRules pathRules;
    private final PathRulesService pathRulesService;
    private final FtpConnectionPool ftpPool;
    private final FtpUtil ftpUtil;
    private final CopyFile copyFile;
    private final FtpSynchronizationStack uploadStack;

    /** 初始化应用内各核心组件和持久化语言。 */
    private AppContext() {
        I18n.configure(config.language());
        logStore=new InMemoryLogStore();
        pathRules=new PathRules();
        pathRulesService=new PathRulesServiceImpl(pathRules);
        ftpPool=new FtpConnectionPool(config);
        ftpUtil=new FtpUtil(ftpPool,logStore);
        copyFile=new CopyFileImpl(ftpUtil);
        uploadStack=new FtpSynchronizationStack(pathRulesService,copyFile,config,logStore);
    }

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
    public FtpSynchronizationStack uploadStack() { return uploadStack; }

    /**
     * 应用配置保存后刷新依赖配置的运行组件。
     */
    public void settingsChanged() { ftpPool.reconfigure(); uploadStack.reschedule(); logStore.info(LocalizedMessage.localized("log.settingsChanged")); }

    /**
     * 关闭后台资源。
     */
    public void shutdown() { uploadStack.shutdown(); ftpPool.close(); }
}
