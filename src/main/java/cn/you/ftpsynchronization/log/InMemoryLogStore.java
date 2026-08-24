package cn.you.ftpsynchronization.log;

import cn.you.ftpsynchronization.pojo.FtpLogEntry;
import cn.you.ftpsynchronization.pojo.LocalizedMessage;
import cn.you.ftpsynchronization.pojo.SystemLogEntry;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 内存日志中心。系统记录和 FTP 文件记录各保留最近 200 条，并支持界面实时监听。
 */
public class InMemoryLogStore {
    private static final int MAX_SIZE=200;
    private final Deque<SystemLogEntry> systemLogs=new ArrayDeque<>();
    private final Deque<FtpLogEntry> ftpLogs=new ArrayDeque<>();
    private final List<Consumer<SystemLogEntry>> systemListeners=new CopyOnWriteArrayList<>();
    private final List<Consumer<FtpLogEntry>> ftpListeners=new CopyOnWriteArrayList<>();

    /** 写入系统信息记录。 */
    public void info(LocalizedMessage message) { addSystem(new SystemLogEntry(LocalDateTime.now(),"INFO",message)); }
    /** 写入系统警告记录。 */
    public void warn(LocalizedMessage message) { addSystem(new SystemLogEntry(LocalDateTime.now(),"WARN",message)); }
    /** 写入系统错误记录。 */
    public void error(LocalizedMessage message) { addSystem(new SystemLogEntry(LocalDateTime.now(),"ERROR",message)); }

    /** 写入系统记录并通知界面。 */
    public void addSystem(SystemLogEntry entry) {
        synchronized (systemLogs) { systemLogs.addFirst(entry); while (systemLogs.size()>MAX_SIZE) systemLogs.removeLast(); }
        systemListeners.forEach(listener -> safeAccept(listener,entry));
    }

    /** 写入 FTP 文件记录并通知界面。 */
    public void addFtp(FtpLogEntry entry) {
        synchronized (ftpLogs) { ftpLogs.addFirst(entry); while (ftpLogs.size()>MAX_SIZE) ftpLogs.removeLast(); }
        ftpListeners.forEach(listener -> safeAccept(listener,entry));
    }

    /** 获取系统记录快照，新记录在前。 */
    public List<SystemLogEntry> systemSnapshot() { synchronized (systemLogs) { return new ArrayList<>(systemLogs); } }
    /** 获取 FTP 记录快照，新记录在前。 */
    public List<FtpLogEntry> ftpSnapshot() { synchronized (ftpLogs) { return new ArrayList<>(ftpLogs); } }
    /** 清空系统记录。 */
    public void clearSystem() { synchronized (systemLogs) { systemLogs.clear(); } }
    /** 清空 FTP 记录。 */
    public void clearFtp() { synchronized (ftpLogs) { ftpLogs.clear(); } }
    /** 添加系统记录监听器并返回注销动作。 */
    public Runnable addSystemListener(Consumer<SystemLogEntry> listener) { if (listener!=null) systemListeners.add(listener); return () -> systemListeners.remove(listener); }
    /** 添加 FTP 记录监听器并返回注销动作。 */
    public Runnable addFtpListener(Consumer<FtpLogEntry> listener) { if (listener!=null) ftpListeners.add(listener); return () -> ftpListeners.remove(listener); }

    /** 安全调用监听器，避免界面异常影响后台任务。 */
    private <T> void safeAccept(Consumer<T> listener,T value) { try { listener.accept(value); } catch (Exception ignored) {} }
}
