package cn.you.ftpsynchronization.stack;

import cn.you.ftpsynchronization.config.AppConfig;
import cn.you.ftpsynchronization.i18n.I18n;
import cn.you.ftpsynchronization.log.InMemoryLogStore;
import cn.you.ftpsynchronization.pojo.FileInfo;
import cn.you.ftpsynchronization.pojo.LocalizedMessage;
import cn.you.ftpsynchronization.pojo.UploadResult;
import cn.you.ftpsynchronization.service.CopyFile;
import cn.you.ftpsynchronization.service.PathRulesService;
import cn.you.ftpsynchronization.utils.TemplateResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FTP 文件传输调度器。上传、下载规则共用同一执行频率；不同规则使用虚拟线程并发，FTP实际并发由连接池大小控制。
 */
public class FtpSynchronizationStack {
    private static final Logger LOG=Logger.getLogger(FtpSynchronizationStack.class.getName());
    private final PathRulesService pathRulesService;
    private final CopyFile copyFile;
    private final AppConfig config;
    private final InMemoryLogStore logStore;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r -> daemonThread(r,"ftp-scheduler"));
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final List<Consumer<UploadEvent>> listeners=new CopyOnWriteArrayList<>();
    private volatile LocalDateTime lastFinishedTime;
    private volatile ScheduledFuture<?> scheduleFuture;
    private volatile boolean started;

    /** 创建文件传输调度器。 */
    public FtpSynchronizationStack(PathRulesService pathRulesService,CopyFile copyFile,AppConfig config,InMemoryLogStore logStore) { this.pathRulesService=pathRulesService; this.copyFile=copyFile; this.config=config; this.logStore=logStore; }

    /** 启动自动传输，首次启动仍保持程序启动立即执行一次。 */
    public synchronized void start() {
        if (started) return;
        started=true; scheduleInternal();
        systemInfo(LocalizedMessage.localized("log.scheduleStarted",scheduleMessage()));
        triggerNow();
    }

    /** 配置修改后取消旧计划并按新频率重新调度。 */
    public synchronized void reschedule() {
        if (!started) return;
        if (scheduleFuture!=null) scheduleFuture.cancel(false);
        scheduleInternal();
        systemInfo(LocalizedMessage.localized("log.scheduleUpdated",scheduleMessage()));
    }

    /** 立即执行一轮文件传输；上一轮仍运行时直接跳过，防止同一轮任务重入。 */
    public boolean triggerNow() {
        if (!running.compareAndSet(false,true)) { LocalizedMessage message=LocalizedMessage.localized("log.busy"); systemWarn(message); publish(message,true); return false; }
        List<FileInfo> rules=new ArrayList<>(pathRulesService.getAllPath().values());
        LocalDateTime now=LocalDateTime.now();
        LocalizedMessage startMessage=LocalizedMessage.localized("log.roundStart",rules.size());
        systemInfo(startMessage);
        publish(startMessage,true);
        if (rules.isEmpty()) { finish(LocalizedMessage.localized("log.noRules")); return true; }
        CompletableFuture<?>[] futures=rules.stream().map(rule -> CompletableFuture.runAsync(() -> processRule(rule,now),workers)).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).whenComplete((v,e) -> {
            if (e!=null) { LOG.log(Level.SEVERE,"Transfer task failed",e); systemError(LocalizedMessage.localized("log.roundError",safeMessage(e))); }
            finish(LocalizedMessage.localized(e==null?"log.roundComplete":"log.roundCompleteError"));
        });
        return true;
    }

    /** 添加调度状态监听器并返回注销动作。 */
    public Runnable addListener(Consumer<UploadEvent> listener) { if (listener!=null) listeners.add(listener); return () -> listeners.remove(listener); }
    /** 获取最近一次完成时间。 */
    public LocalDateTime getLastFinishedTime() { return lastFinishedTime; }
    /** 判断当前是否有文件传输任务运行。 */
    public boolean isRunning() { return running.get(); }
    /** 获取当前计划描述。 */
    public String scheduleDescription() { return scheduleMessage().render(); }
    /** 关闭调度器和虚拟线程执行器。 */
    public void shutdown() { if (scheduleFuture!=null) scheduleFuture.cancel(true); scheduler.shutdownNow(); workers.shutdownNow(); }

    /** 根据规则传输方向分发到上传或下载逻辑。 */
    private void processRule(FileInfo rule,LocalDateTime baseTime) {
        String name=safeName(rule);
        if (rule==null||!rule.getIsRunning()) return;
        try {
            LocalDateTime adjusted=baseTime.plusMinutes(rule.getDelayTime());
            String dynamicPath=TemplateResolver.resolve(rule,adjusted);
            Path localDirectory=resolveLocalDirectory(rule.getBasePath(),dynamicPath);
            String remoteDirectory=TemplateResolver.joinRemote(rule.getRemoteBasePath(),dynamicPath);
            int latestFileCount=rule.getLatestFileCount()==null?config.latestFileCount():rule.getLatestFileCount();
            if (rule.isDownload()) processDownloadRule(name,remoteDirectory,localDirectory,Math.max(1,latestFileCount));
            else processUploadRule(name,remoteDirectory,localDirectory,Math.max(1,latestFileCount));
        } catch (Exception e) { LOG.log(Level.SEVERE,"Rule failed: "+name,e); systemError(LocalizedMessage.localized("log.ruleError",name,safeMessage(e))); }
    }

    /** 执行上传规则，仍使用原来的本地最新N个稳定文件逻辑。 */
    private void processUploadRule(String name,String remoteDirectory,Path localDirectory,int latestFileCount) throws IOException {
        if (!Files.isDirectory(localDirectory)) { systemWarn(LocalizedMessage.localized("log.uploadDirMissing",name,localDirectory)); return; }
        List<Path> candidates=latestStableFiles(localDirectory,latestFileCount,config.fileStableSeconds());
        if (candidates.isEmpty()) { systemInfo(LocalizedMessage.localized("log.noCandidates",name)); return; }
        UploadResult result=copyFile.syncMissingFiles(name,remoteDirectory,candidates);
        LocalizedMessage message=LocalizedMessage.localized("log.uploadSummary",name,result.uploaded(),result.skipped(),result.failed());
        if (result.failed()>0) systemWarn(message); else systemInfo(message);
    }

    /** 执行下载规则，从FTP目录最新N个文件中下载本地缺失文件。 */
    private void processDownloadRule(String name,String remoteDirectory,Path localDirectory,int latestFileCount) {
        UploadResult result=copyFile.syncMissingDownloads(name,remoteDirectory,localDirectory,latestFileCount);
        LocalizedMessage message=LocalizedMessage.localized("log.downloadSummary",name,result.uploaded(),result.skipped(),result.failed());
        if (result.failed()>0) systemWarn(message); else systemInfo(message);
    }

    /** 生成本地动态目录并阻止规则路径逃逸出本地根目录。 */
    private Path resolveLocalDirectory(String basePath,String dynamicPath) {
        if (basePath==null||basePath.isBlank()) throw new IllegalArgumentException(I18n.text("validation.localRoot"));
        Path base=Path.of(basePath).toAbsolutePath().normalize();
        String relative=dynamicPath==null?"":dynamicPath.replace('\\','/').replaceAll("^/+","");
        Path resolved=relative.isBlank()?base:base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) throw new IllegalArgumentException(I18n.text("validation.pathEscapeDetail",dynamicPath));
        return resolved;
    }

    /** 按文件名排序取最后N个稳定普通文件，保持原上传规则的最新文件选择逻辑。 */
    private List<Path> latestStableFiles(Path directory,int count,int stableSeconds) throws IOException {
        long threshold=System.currentTimeMillis()-TimeUnit.SECONDS.toMillis(stableSeconds);
        Comparator<Path> byName=Comparator.comparing(path -> path.getFileName().toString());
        PriorityQueue<Path> latest=new PriorityQueue<>(Math.max(1,count),byName);
        try (var stream=Files.list(directory)) {
            stream.filter(Files::isRegularFile).filter(path -> lastModified(path)<=threshold).forEach(path -> {
                if (latest.size()<count) latest.offer(path);
                else if (byName.compare(path,latest.peek())>0) { latest.poll(); latest.offer(path); }
            });
        }
        return latest.stream().sorted(byName).toList();
    }

    /** 获取文件修改时间，读取失败时按不稳定处理。 */
    private long lastModified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException e) { return Long.MAX_VALUE; } }
    /** 创建当前配置对应的定时计划。 */
    private void scheduleInternal() { int interval=config.uploadIntervalSeconds(); long initialDelay=interval==60?secondsUntilNextRun(config.scheduleSecond()):interval; scheduleFuture=scheduler.scheduleAtFixedRate(this::triggerNow,Math.max(1,initialDelay),interval,TimeUnit.SECONDS); }
    /** 创建可随界面语言重新渲染的调度频率描述。 */
    private LocalizedMessage scheduleMessage() { int seconds=config.uploadIntervalSeconds(); return seconds%3600==0?LocalizedMessage.localized("schedule.hours",seconds/3600):seconds%60==0?LocalizedMessage.localized("schedule.minutes",seconds/60):LocalizedMessage.localized("schedule.seconds",seconds); }
    /** 计算一分钟周期到目标秒数的首次延迟。 */
    private long secondsUntilNextRun(int targetSecond) { LocalDateTime now=LocalDateTime.now(),next=now.withSecond(targetSecond).withNano(0); if (!next.isAfter(now)) next=next.plusMinutes(1); return Math.max(1,Duration.between(now,next).toSeconds()); }
    /** 完成本轮任务并发布状态。 */
    private void finish(LocalizedMessage message) { lastFinishedTime=LocalDateTime.now(); running.set(false); systemInfo(message); publish(message,false); }
    /** 发布界面状态事件。 */
    private void publish(LocalizedMessage message,boolean busy) { UploadEvent event=new UploadEvent(LocalDateTime.now(),message,busy); listeners.forEach(listener -> { try { listener.accept(event); } catch (Exception ignored) {} }); }
    /** 写系统INFO日志。 */
    private void systemInfo(LocalizedMessage message) { logStore.info(message); }
    /** 写系统WARN日志。 */
    private void systemWarn(LocalizedMessage message) { logStore.warn(message); }
    /** 写系统ERROR日志。 */
    private void systemError(LocalizedMessage message) { logStore.error(message); }
    /** 创建守护调度线程。 */
    private static Thread daemonThread(Runnable runnable,String name) { Thread thread=new Thread(runnable,name); thread.setDaemon(true); return thread; }
    /** 获取规则显示名称。 */
    private String safeName(FileInfo rule) { return rule==null||rule.getName()==null||rule.getName().isBlank()?I18n.text("log.unnamedRule"):rule.getName(); }
    /** 获取异常安全描述。 */
    private String safeMessage(Throwable e) { return e==null?I18n.text("ftp.unknownError"):e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }

    /** 调度状态事件。 */
    public record UploadEvent(LocalDateTime time,LocalizedMessage message,boolean busy) {}
}
