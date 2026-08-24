package cn.you.ftpupload.stack;

import cn.you.ftpupload.config.AppConfig;
import cn.you.ftpupload.log.InMemoryLogStore;
import cn.you.ftpupload.pojo.FileInfo;
import cn.you.ftpupload.pojo.UploadResult;
import cn.you.ftpupload.service.CopyFile;
import cn.you.ftpupload.service.PathRulesService;
import cn.you.ftpupload.utils.TemplateResolver;

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
public class FtpUploadStack {
    private static final Logger LOG=Logger.getLogger(FtpUploadStack.class.getName());
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
    public FtpUploadStack(PathRulesService pathRulesService,CopyFile copyFile,AppConfig config,InMemoryLogStore logStore) { this.pathRulesService=pathRulesService; this.copyFile=copyFile; this.config=config; this.logStore=logStore; }

    /** 启动自动传输，首次启动仍保持程序启动立即执行一次。 */
    public synchronized void start() {
        if (started) return;
        started=true; scheduleInternal();
        systemInfo("程序启动，自动文件传输已启用："+scheduleDescription());
        triggerNow();
    }

    /** 配置修改后取消旧计划并按新频率重新调度。 */
    public synchronized void reschedule() {
        if (!started) return;
        if (scheduleFuture!=null) scheduleFuture.cancel(false);
        scheduleInternal();
        systemInfo("文件传输频率已更新："+scheduleDescription());
    }

    /** 立即执行一轮文件传输；上一轮仍运行时直接跳过，防止同一轮任务重入。 */
    public boolean triggerNow() {
        if (!running.compareAndSet(false,true)) { systemWarn("已有文件传输任务正在执行，本次触发已跳过"); publish("已有文件传输任务正在执行，本次触发已跳过",true); return false; }
        List<FileInfo> rules=new ArrayList<>(pathRulesService.getAllPath().values());
        LocalDateTime now=LocalDateTime.now();
        systemInfo("开始文件传输检查，共 "+rules.size()+" 条规则");
        publish("开始检查 "+rules.size()+" 条传输规则",true);
        if (rules.isEmpty()) { finish("没有可执行的传输规则"); return true; }
        CompletableFuture<?>[] futures=rules.stream().map(rule -> CompletableFuture.runAsync(() -> processRule(rule,now),workers)).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).whenComplete((v,e) -> {
            if (e!=null) { LOG.log(Level.SEVERE,"文件传输任务存在异常",e); systemError("本轮文件传输检查存在异常："+safeMessage(e)); }
            finish(e==null?"本轮文件传输检查完成":"本轮文件传输检查完成，但存在异常");
        });
        return true;
    }

    /** 添加调度状态监听器。 */
    public void addListener(Consumer<UploadEvent> listener) { if (listener!=null) listeners.add(listener); }
    /** 获取最近一次完成时间。 */
    public LocalDateTime getLastFinishedTime() { return lastFinishedTime; }
    /** 判断当前是否有文件传输任务运行。 */
    public boolean isRunning() { return running.get(); }
    /** 获取当前计划描述。 */
    public String scheduleDescription() { int seconds=config.uploadIntervalSeconds(); return seconds%3600==0?"每 "+(seconds/3600)+" 小时":seconds%60==0?"每 "+(seconds/60)+" 分钟":"每 "+seconds+" 秒"; }
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
        } catch (Exception e) { LOG.log(Level.SEVERE,"规则执行失败: "+name,e); systemError("规则["+name+"]执行失败："+safeMessage(e)); }
    }

    /** 执行上传规则，仍使用原来的本地最新N个稳定文件逻辑。 */
    private void processUploadRule(String name,String remoteDirectory,Path localDirectory,int latestFileCount) throws IOException {
        if (!Files.isDirectory(localDirectory)) { systemWarn("上传规则["+name+"]本地目录不存在，已跳过："+localDirectory); return; }
        List<Path> candidates=latestStableFiles(localDirectory,latestFileCount,config.fileStableSeconds());
        if (candidates.isEmpty()) { systemInfo("上传规则["+name+"]没有符合稳定时间的候选文件"); return; }
        UploadResult result=copyFile.syncMissingFiles(name,remoteDirectory,candidates);
        String message="上传规则["+name+"]完成：上传 "+result.uploaded()+"，远端已存在 "+result.skipped()+"，失败 "+result.failed();
        if (result.failed()>0) systemWarn(message); else systemInfo(message);
    }

    /** 执行下载规则，从FTP目录最新N个文件中下载本地缺失文件。 */
    private void processDownloadRule(String name,String remoteDirectory,Path localDirectory,int latestFileCount) {
        UploadResult result=copyFile.syncMissingDownloads(name,remoteDirectory,localDirectory,latestFileCount);
        String message="下载规则["+name+"]完成：下载 "+result.uploaded()+"，本地已存在 "+result.skipped()+"，失败 "+result.failed();
        if (result.failed()>0) systemWarn(message); else systemInfo(message);
    }

    /** 生成本地动态目录并阻止规则路径逃逸出本地根目录。 */
    private Path resolveLocalDirectory(String basePath,String dynamicPath) {
        if (basePath==null||basePath.isBlank()) throw new IllegalArgumentException("本地根目录不能为空");
        Path base=Path.of(basePath).toAbsolutePath().normalize();
        String relative=dynamicPath==null?"":dynamicPath.replace('\\','/').replaceAll("^/+","");
        Path resolved=relative.isBlank()?base:base.resolve(relative).normalize();
        if (!resolved.startsWith(base)) throw new IllegalArgumentException("规则路径不能跳出本地根目录："+dynamicPath);
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
    /** 计算一分钟周期到目标秒数的首次延迟。 */
    private long secondsUntilNextRun(int targetSecond) { LocalDateTime now=LocalDateTime.now(),next=now.withSecond(targetSecond).withNano(0); if (!next.isAfter(now)) next=next.plusMinutes(1); return Math.max(1,Duration.between(now,next).toSeconds()); }
    /** 完成本轮任务并发布状态。 */
    private void finish(String message) { lastFinishedTime=LocalDateTime.now(); running.set(false); systemInfo(message); publish(message,false); }
    /** 发布界面状态事件。 */
    private void publish(String message,boolean busy) { UploadEvent event=new UploadEvent(LocalDateTime.now(),message,busy); listeners.forEach(listener -> { try { listener.accept(event); } catch (Exception ignored) {} }); }
    /** 写系统INFO日志。 */
    private void systemInfo(String message) { logStore.info(message); }
    /** 写系统WARN日志。 */
    private void systemWarn(String message) { logStore.warn(message); }
    /** 写系统ERROR日志。 */
    private void systemError(String message) { logStore.error(message); }
    /** 创建守护调度线程。 */
    private static Thread daemonThread(Runnable runnable,String name) { Thread thread=new Thread(runnable,name); thread.setDaemon(true); return thread; }
    /** 获取规则显示名称。 */
    private String safeName(FileInfo rule) { return rule==null||rule.getName()==null||rule.getName().isBlank()?"未命名规则":rule.getName(); }
    /** 获取异常安全描述。 */
    private String safeMessage(Throwable e) { return e==null?"未知错误":e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }

    /** 调度状态事件。 */
    public record UploadEvent(LocalDateTime time,String message,boolean busy) {}
}
