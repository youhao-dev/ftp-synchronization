package cn.you.ftpsynchronization.utils;

import cn.you.ftpsynchronization.config.AppConfig;
import cn.you.ftpsynchronization.config.AppSettings;
import cn.you.ftpsynchronization.i18n.I18n;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量 FTP 连接池。每个 FTPClient 同一时刻只会被一个任务使用，配置变化时通过 generation 淘汰旧连接。
 */
public class FtpConnectionPool implements AutoCloseable {
    private final AppConfig config;
    private final LinkedBlockingQueue<PooledConnection> idle=new LinkedBlockingQueue<>();
    private final AtomicInteger total=new AtomicInteger();
    private final AtomicLong generation=new AtomicLong(1);
    private final AtomicBoolean closed=new AtomicBoolean(false);

    /** 创建使用动态应用配置的 FTP 连接池。 */
    public FtpConnectionPool(AppConfig config) { this.config=config; }

    /**
     * 借用一个 FTP 连接，连接池满时等待配置的 borrow timeout。
     */
    public Lease borrow() throws IOException {
        if (closed.get()) throw new IOException(I18n.text("ftp.poolClosed"));
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.poolBorrowTimeoutMs());
        while (true) {
            PooledConnection pooled=idle.poll();
            if (pooled!=null) {
                if (prepare(pooled)) return new Lease(this,pooled);
                destroy(pooled);
                continue;
            }
            if (tryReserveSlot()) {
                long gen=generation.get();
                try { FTPClient client=createClient(); String home=client.printWorkingDirectory(); return new Lease(this,new PooledConnection(client,gen,home==null||home.isBlank()?"/":home)); }
                catch (Exception e) { total.decrementAndGet(); throw e instanceof IOException io?io:new IOException(e); }
            }
            long remain=deadline-System.nanoTime();
            if (remain<=0) throw new IOException(I18n.text("ftp.poolTimeout"));
            try {
                pooled=idle.poll(remain,TimeUnit.NANOSECONDS);
                if (pooled==null) throw new IOException(I18n.text("ftp.poolTimeout"));
                if (prepare(pooled)) return new Lease(this,pooled);
                destroy(pooled);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(I18n.text("ftp.poolInterrupted"),e); }
        }
    }

    /**
     * 配置修改后让空闲旧连接立即失效；正在使用的旧连接归还时自动销毁。
     */
    public void reconfigure() {
        generation.incrementAndGet();
        PooledConnection connection;
        while ((connection=idle.poll())!=null) destroy(connection);
    }

    /** 获取已创建连接数量。 */
    public int totalCount() { return total.get(); }
    /** 获取当前空闲连接数量。 */
    public int idleCount() { return idle.size(); }

    /**
     * 关闭连接池并释放所有空闲连接。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false,true)) return;
        generation.incrementAndGet();
        PooledConnection connection;
        while ((connection=idle.poll())!=null) destroy(connection);
    }

    /**
     * 创建并初始化 FTPClient。
     */
    private FTPClient createClient() throws IOException {
        AppSettings settings=config.snapshot(); //一次连接使用同一份配置快照，避免保存设置过程中读到混合参数
        if (settings.ftpHost().isBlank()) throw new IOException(I18n.text("ftp.notConfigured"));
        FTPClient ftp=new FTPClient();
        ftp.setControlEncoding("UTF-8"); ftp.setBufferSize(64*1024); ftp.setConnectTimeout(settings.connectTimeoutMs()); ftp.setDefaultTimeout(settings.readTimeoutMs()); ftp.setDataTimeout(Duration.ofMillis(settings.dataTimeoutMs()));
        ftp.setControlKeepAliveTimeout(Duration.ofSeconds(30)); ftp.setControlKeepAliveReplyTimeout(Duration.ofSeconds(5));
        try {
            ftp.connect(settings.ftpHost(),settings.ftpPort());
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) throw new IOException(I18n.text("ftp.serverRejected",ftp.getReplyString()));
            ftp.setSoTimeout(settings.readTimeoutMs());
            if (!ftp.login(settings.ftpUsername(),settings.ftpPassword())) throw new IOException(I18n.text("ftp.loginFailed",ftp.getReplyString()));
            ftp.enterLocalPassiveMode(); ftp.setFileTransferMode(FTP.STREAM_TRANSFER_MODE); ftp.setFileType(FTP.BINARY_FILE_TYPE);
            return ftp;
        } catch (Exception e) {
            disconnect(ftp);
            throw e instanceof IOException io?io:new IOException(I18n.text("ftp.connectionFailed"),e);
        }
    }

    /**
     * CAS 预留一个连接槽位，保证总连接数不超过配置值。
     */
    private boolean tryReserveSlot() {
        while (true) {
            int current=total.get(),max=config.ftpPoolSize();
            if (current>=max) return false;
            if (total.compareAndSet(current,current+1)) return true;
        }
    }

    /**
     * 校验空闲连接并恢复到登录初始目录。
     */
    private boolean prepare(PooledConnection pooled) {
        if (pooled.generation()!=generation.get()||pooled.client()==null||!pooled.client().isConnected()) return false;
        try { return pooled.client().sendNoOp()&&pooled.client().changeWorkingDirectory(pooled.homeDirectory()); } catch (Exception e) { return false; }
    }

    /**
     * 归还连接；异常连接、旧代连接或超出新池大小的连接直接销毁。
     */
    private void release(PooledConnection pooled,boolean broken) {
        if (pooled==null) return;
        if (broken||closed.get()||pooled.generation()!=generation.get()||total.get()>config.ftpPoolSize()) { destroy(pooled); return; }
        try {
            if (!pooled.client().isConnected()||!pooled.client().sendNoOp()) { destroy(pooled); return; }
            pooled.client().changeWorkingDirectory(pooled.homeDirectory());
            if (!idle.offer(pooled)) destroy(pooled);
        } catch (Exception e) { destroy(pooled); }
    }

    /** 销毁一个池连接并更新数量。 */
    private void destroy(PooledConnection pooled) { if (pooled==null) return; disconnect(pooled.client()); total.updateAndGet(value -> Math.max(0,value-1)); }
    /** 安全登出并断开 FTPClient。 */
    private void disconnect(FTPClient ftp) { if (ftp==null) return; try { if (ftp.isConnected()) ftp.logout(); } catch (Exception ignored) {} try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {} }

    /** 连接池内部连接包装。 */
    private record PooledConnection(FTPClient client,long generation,String homeDirectory) {}

    /**
     * 借出连接句柄。try-with-resources 关闭 Lease 会把连接归还到池中。
     */
    public static final class Lease implements AutoCloseable {
        private final FtpConnectionPool pool;
        private final PooledConnection pooled;
        private boolean broken;
        private boolean closed;

        private Lease(FtpConnectionPool pool,PooledConnection pooled) { this.pool=pool; this.pooled=pooled; }
        /** 获取当前独占的 FTPClient。 */
        public FTPClient client() { return pooled.client(); }
        /** 标记连接异常，归还时直接销毁。 */
        public void invalidate() { broken=true; }
        /** 归还连接。 */
        @Override public void close() { if (!closed) { closed=true; pool.release(pooled,broken); } }
    }
}
