package cn.you.ftpupload.service.impl;

import cn.you.ftpupload.pojo.UploadResult;
import cn.you.ftpupload.service.CopyFile;
import cn.you.ftpupload.utils.FtpUtil;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FTP 文件上传、下载同步实现。
 */
public class CopyFileImpl implements CopyFile {
    private static final Logger LOG=Logger.getLogger(CopyFileImpl.class.getName());
    private final FtpUtil ftpUtil;

    /** 创建文件同步服务。 */
    public CopyFileImpl(FtpUtil ftpUtil) { this.ftpUtil=ftpUtil; }
    /** 上传输入流。 */
    @Override public boolean fileUpload(String path,String filename,InputStream input) { return ftpUtil.fileUpload(path,filename,input); }

    /** 上传本地目录中的指定文件并自动关闭输入流。 */
    @Override public boolean fileUpload(String path,String filename,String localBasePath) {
        try (InputStream input=new BufferedInputStream(Files.newInputStream(Path.of(localBasePath,filename)))) { return ftpUtil.fileUpload(path,filename,input); }
        catch (Exception e) { LOG.log(Level.SEVERE,"文件上传失败: "+filename,e); return false; }
    }

    /** 列出远程目录文件。 */
    @Override public List<String> listRemoteFiles(String directory) { return ftpUtil.listDirectory(directory); }
    /** 单连接同步上传缺失文件。 */
    @Override public UploadResult syncMissingFiles(String ruleName,String remoteDirectory,List<Path> localFiles) { return ftpUtil.syncMissingFiles(ruleName,remoteDirectory,localFiles); }
    /** 单连接同步下载缺失文件。 */
    @Override public UploadResult syncMissingDownloads(String ruleName,String remoteDirectory,Path localDirectory,int latestFileCount) { return ftpUtil.syncMissingDownloads(ruleName,remoteDirectory,localDirectory,latestFileCount); }
}
