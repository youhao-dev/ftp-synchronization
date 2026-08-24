package cn.you.ftpupload.utils;

import cn.you.ftpupload.log.InMemoryLogStore;
import cn.you.ftpupload.pojo.FtpLogEntry;
import cn.you.ftpupload.pojo.UploadResult;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FTP 操作工具。上传和下载都使用连接池，批量同步期间复用同一连接，并把最近文件操作记录写入内存日志。
 */
public class FtpUtil {
    private static final Logger LOG=Logger.getLogger(FtpUtil.class.getName());
    private final FtpConnectionPool pool;
    private final InMemoryLogStore logStore;

    /** 创建 FTP 操作工具。 */
    public FtpUtil(FtpConnectionPool pool,InMemoryLogStore logStore) { this.pool=pool; this.logStore=logStore; }

    /** 上传单个输入流。用于兼容原接口，正式定时上传优先使用 syncMissingFiles。 */
    public boolean fileUpload(String path,String filename,InputStream input) {
        try (FtpConnectionPool.Lease lease=pool.borrow()) {
            FTPClient ftp=lease.client();
            try {
                ensureDirectory(ftp,path);
                boolean success=ftp.storeFile(filename,input);
                if (!success) LOG.warning("FTP 上传失败: "+path+"/"+filename+"，"+ftp.getReplyString());
                return success;
            } catch (Exception e) { lease.invalidate(); throw e; }
        } catch (Exception e) { LOG.log(Level.SEVERE,"FTP 上传异常: "+path+"/"+filename,e); return false; }
    }

    /** 列出远程目录下普通文件名，目录不存在或连接失败时返回空列表。 */
    public List<String> listDirectory(String directory) {
        try (FtpConnectionPool.Lease lease=pool.borrow()) {
            FTPClient ftp=lease.client();
            try {
                if (!changeDirectory(ftp,directory)) return List.of();
                FTPFile[] files=ftp.listFiles();
                return files==null?List.of():Arrays.stream(files).filter(FTPFile::isFile).map(FTPFile::getName).toList();
            } catch (Exception e) { lease.invalidate(); throw e; }
        } catch (Exception e) { LOG.log(Level.SEVERE,"读取 FTP 目录失败: "+directory,e); return List.of(); }
    }

    /** 在一个池连接中完成远端文件名比对和多文件上传，远端已有文件不会重复上传。 */
    public UploadResult syncMissingFiles(String ruleName,String remoteDirectory,List<Path> localFiles) {
        if (localFiles==null||localFiles.isEmpty()) return new UploadResult(0,0,0);
        int uploaded=0,skipped=0,failed=0;
        try (FtpConnectionPool.Lease lease=pool.borrow()) {
            FTPClient ftp=lease.client();
            try {
                ensureDirectory(ftp,remoteDirectory);
                FTPFile[] listed=ftp.listFiles();
                Set<String> remoteNames=new HashSet<>(listed==null?Set.of():Arrays.stream(listed).filter(FTPFile::isFile).map(FTPFile::getName).toList());
                for (int i=0;i<localFiles.size();i++) {
                    Path file=localFiles.get(i);
                    String name=file.getFileName().toString(),remotePath=TemplateResolver.joinRemote(remoteDirectory,name);
                    long size=fileSize(file);
                    if (remoteNames.contains(name)) {
                        skipped++;
                        logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,file.toString(),remotePath,"已存在",size,0,"远端已存在，跳过上传"));
                        continue;
                    }
                    long start=System.nanoTime();
                    try (InputStream input=new BufferedInputStream(Files.newInputStream(file),64*1024)) {
                        boolean success=ftp.storeFile(name,input);
                        long cost=elapsedMs(start);
                        if (success) {
                            uploaded++; remoteNames.add(name);
                            logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,file.toString(),remotePath,"成功",size,cost,"上传成功"));
                            LOG.info("上传成功: "+remotePath);
                        } else {
                            failed++;
                            logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,file.toString(),remotePath,"失败",size,cost,trimReply(ftp.getReplyString())));
                            LOG.warning("上传失败: "+remotePath+"，"+ftp.getReplyString());
                        }
                    } catch (Exception e) {
                        long cost=elapsedMs(start); failed++;
                        logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,file.toString(),remotePath,"失败",size,cost,safeMessage(e)));
                        lease.invalidate();
                        markRemainingUploadFailed(ruleName,remoteDirectory,localFiles,i+1,"FTP 连接异常，本轮剩余文件未上传");
                        failed+=Math.max(0,localFiles.size()-i-1);
                        break;
                    }
                }
            } catch (Exception e) { lease.invalidate(); throw e; }
        } catch (Exception e) {
            int already=uploaded+skipped+failed,remain=Math.max(0,localFiles.size()-already);
            if (remain>0) { markRemainingUploadFailed(ruleName,remoteDirectory,localFiles,already,safeMessage(e)); failed+=remain; }
            logStore.error("FTP 上传同步失败 ["+safe(ruleName)+"]："+safeMessage(e));
            LOG.log(Level.SEVERE,"FTP 上传同步失败: "+remoteDirectory,e);
        }
        return new UploadResult(uploaded,skipped,failed);
    }

    /** 在一个池连接中读取FTP最新N个文件并下载本地缺失文件；返回值uploaded字段在此表示成功下载数量。 */
    public UploadResult syncMissingDownloads(String ruleName,String remoteDirectory,Path localDirectory,int latestFileCount) {
        int downloaded=0,skipped=0,failed=0;
        Path localBase;
        try { localBase=localDirectory.toAbsolutePath().normalize(); Files.createDirectories(localBase); }
        catch (Exception e) { logStore.error("创建下载目录失败 ["+safe(ruleName)+"]："+safeMessage(e)); return new UploadResult(0,0,1); }
        List<FTPFile> candidates=List.of();
        try (FtpConnectionPool.Lease lease=pool.borrow()) {
            FTPClient ftp=lease.client();
            try {
                if (!changeDirectory(ftp,remoteDirectory)) { logStore.warn("FTP 下载目录不存在 ["+safe(ruleName)+"]："+remoteDirectory); return new UploadResult(0,0,0); }
                FTPFile[] listed=ftp.listFiles();
                List<FTPFile> remoteFiles=listed==null?List.of():Arrays.stream(listed).filter(FTPFile::isFile).filter(file -> file.getName()!=null&&!file.getName().isBlank()).sorted(Comparator.comparing(FTPFile::getName)).toList();
                int count=Math.max(1,latestFileCount),from=Math.max(0,remoteFiles.size()-count);
                candidates=remoteFiles.subList(from,remoteFiles.size());
                for (int i=0;i<candidates.size();i++) {
                    FTPFile remoteFile=candidates.get(i);
                    String name=remoteFile.getName(),remotePath=TemplateResolver.joinRemote(remoteDirectory,name);
                    long size=Math.max(0,remoteFile.getSize());
                    Path localFile=safeLocalTarget(localBase,name);
                    if (Files.isRegularFile(localFile)) {
                        skipped++;
                        logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,localFile.toString(),remotePath,"已存在",size,0,"本地已存在，跳过下载"));
                        continue;
                    }
                    long start=System.nanoTime();
                    Path temp=Files.createTempFile(localBase,"ftp-",".part");
                    try {
                        boolean success;
                        try (OutputStream output=new BufferedOutputStream(Files.newOutputStream(temp),64*1024)) { success=ftp.retrieveFile(name,output); }
                        long cost=elapsedMs(start),actualSize=fileSize(temp);
                        if (success&&(remoteFile.getSize()<0||actualSize==remoteFile.getSize())) {
                            moveDownloadedFile(temp,localFile);
                            downloaded++;
                            logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,localFile.toString(),remotePath,"成功",actualSize,cost,"下载成功"));
                            LOG.info("下载成功: "+remotePath+" -> "+localFile);
                        } else {
                            Files.deleteIfExists(temp); failed++;
                            String message=success?"下载文件大小校验失败，远端="+remoteFile.getSize()+"，本地="+actualSize:trimReply(ftp.getReplyString());
                            logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,localFile.toString(),remotePath,"失败",size,cost,message));
                            LOG.warning("下载失败: "+remotePath+"，"+message);
                        }
                    } catch (Exception e) {
                        deleteQuietly(temp);
                        long cost=elapsedMs(start); failed++;
                        logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,localFile.toString(),remotePath,"失败",size,cost,safeMessage(e)));
                        lease.invalidate();
                        markRemainingDownloadFailed(ruleName,remoteDirectory,localBase,candidates,i+1,"FTP 连接异常，本轮剩余文件未下载");
                        failed+=Math.max(0,candidates.size()-i-1);
                        break;
                    }
                }
            } catch (Exception e) { lease.invalidate(); throw e; }
        } catch (Exception e) {
            int already=downloaded+skipped+failed,remain=Math.max(0,candidates.size()-already);
            if (remain>0) { markRemainingDownloadFailed(ruleName,remoteDirectory,localBase,candidates,already,safeMessage(e)); failed+=remain; }
            logStore.error("FTP 下载同步失败 ["+safe(ruleName)+"]："+safeMessage(e));
            LOG.log(Level.SEVERE,"FTP 下载同步失败: "+remoteDirectory,e);
        }
        return new UploadResult(downloaded,skipped,failed);
    }

    /** 使用界面当前输入参数创建临时连接测试，不影响正式连接池。 */
    public static String testConnection(String host,int port,String username,String password,int connectTimeoutMs,int readTimeoutMs,int dataTimeoutMs) throws IOException {
        FTPClient ftp=new FTPClient();
        ftp.setControlEncoding("UTF-8"); ftp.setConnectTimeout(connectTimeoutMs); ftp.setDefaultTimeout(readTimeoutMs); ftp.setDataTimeout(Duration.ofMillis(dataTimeoutMs));
        try {
            ftp.connect(host,port);
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) throw new IOException("服务器拒绝连接: "+trimReplyStatic(ftp.getReplyString()));
            ftp.setSoTimeout(readTimeoutMs);
            if (!ftp.login(username,password)) throw new IOException("登录失败: "+trimReplyStatic(ftp.getReplyString()));
            ftp.enterLocalPassiveMode(); ftp.setFileTransferMode(FTP.STREAM_TRANSFER_MODE); ftp.setFileType(FTP.BINARY_FILE_TYPE);
            if (!ftp.sendNoOp()) throw new IOException("NOOP 测试失败: "+trimReplyStatic(ftp.getReplyString()));
            return "连接成功，服务器响应："+trimReplyStatic(ftp.getReplyString());
        } finally {
            try { if (ftp.isConnected()) ftp.logout(); } catch (Exception ignored) {}
            try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {}
        }
    }

    /** 确保远程目录存在并切换到该目录。 */
    private void ensureDirectory(FTPClient ftp,String directory) throws IOException {
        String normalized=normalizeRemote(directory);
        if ("/".equals(normalized)||normalized.isBlank()) { ftp.changeWorkingDirectory("/"); return; }
        if (normalized.startsWith("/")) ftp.changeWorkingDirectory("/");
        for (String part:normalized.split("/")) {
            if (part.isBlank()) continue;
            if (!ftp.changeWorkingDirectory(part)) {
                if (!ftp.makeDirectory(part)||!ftp.changeWorkingDirectory(part)) throw new IOException("创建远程目录失败: "+directory+"，"+trimReply(ftp.getReplyString()));
            }
        }
    }

    /** 尝试切换到远程目录。 */
    private boolean changeDirectory(FTPClient ftp,String directory) throws IOException { return ftp.changeWorkingDirectory(normalizeRemote(directory)); }
    /** 规范化 FTP 路径。 */
    private String normalizeRemote(String path) { if (path==null||path.isBlank()) return "/"; String value=path.replace('\\','/').replaceAll("/{2,}","/"); return value.length()>1&&value.endsWith("/")?value.substring(0,value.length()-1):value; }

    /** 生成安全的本地下载目标，阻止FTP文件名跳出目标目录。 */
    private Path safeLocalTarget(Path localDirectory,String filename) throws IOException {
        if (filename==null||filename.isBlank()||filename.contains("/")||filename.contains("\\")||".".equals(filename)||"..".equals(filename)) throw new IOException("FTP 文件名不安全: "+filename);
        Path target=localDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(localDirectory)) throw new IOException("FTP 文件名越界: "+filename);
        return target;
    }

    /** 把下载临时文件移动为正式文件，文件系统不支持原子移动时自动降级。 */
    private void moveDownloadedFile(Path temp,Path target) throws IOException {
        try { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING); }
    }

    /** 把本轮尚未上传的文件写成失败记录，避免连接失败时界面没有明细。 */
    private void markRemainingUploadFailed(String ruleName,String remoteDirectory,List<Path> files,int start,String message) {
        for (int i=Math.max(0,start);i<files.size();i++) {
            Path file=files.get(i);
            logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),file.getFileName().toString(),file.toString(),TemplateResolver.joinRemote(remoteDirectory,file.getFileName().toString()),"失败",fileSize(file),0,message));
        }
    }

    /** 把本轮尚未下载的文件写成失败记录。 */
    private void markRemainingDownloadFailed(String ruleName,String remoteDirectory,Path localDirectory,List<FTPFile> files,int start,String message) {
        for (int i=Math.max(0,start);i<files.size();i++) {
            FTPFile file=files.get(i); String name=file.getName(); Path localFile=localDirectory.resolve(name).normalize();
            logStore.addFtp(new FtpLogEntry(LocalDateTime.now(),safe(ruleName),name,localFile.toString(),TemplateResolver.joinRemote(remoteDirectory,name),"失败",Math.max(0,file.getSize()),0,message));
        }
    }

    /** 安静删除临时文件，清理失败不覆盖原始传输异常。 */
    private void deleteQuietly(Path file) { try { if (file!=null) Files.deleteIfExists(file); } catch (Exception ignored) {} }
    /** 获取文件大小，失败时返回0。 */
    private long fileSize(Path file) { try { return Files.size(file); } catch (Exception e) { return 0; } }
    /** 计算耗时毫秒。 */
    private long elapsedMs(long startNano) { return Math.max(0,TimeUnitHelper.nanosToMillis(System.nanoTime()-startNano)); }
    /** 清理 FTP 响应中的换行。 */
    private String trimReply(String reply) { return trimReplyStatic(reply); }
    /** 清理 FTP 响应中的换行。 */
    private static String trimReplyStatic(String reply) { return reply==null?"":reply.replace("\r"," ").replace("\n"," ").trim(); }
    /** 获取安全异常信息。 */
    private String safeMessage(Exception e) { return e==null?"未知错误":e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
    /** null字符串转空字符串。 */
    private String safe(String value) { return value==null?"":value; }
    /** 仅用于保持业务代码中的纳秒转毫秒调用集中。 */
    private static final class TimeUnitHelper { /** 纳秒转毫秒。 */ private static long nanosToMillis(long nanos) { return nanos/1_000_000L; } }
}
