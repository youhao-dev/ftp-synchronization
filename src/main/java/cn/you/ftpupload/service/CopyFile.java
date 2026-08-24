package cn.you.ftpupload.service;

import cn.you.ftpupload.pojo.UploadResult;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * FTP 文件上传、下载同步接口。
 */
public interface CopyFile {
    /** 上传输入流到指定FTP目录。 */
    boolean fileUpload(String path,String filename,InputStream input);
    /** 上传本地目录中的指定文件。 */
    boolean fileUpload(String path,String filename,String localBasePath);
    /** 列出FTP指定目录中的普通文件名。 */
    List<String> listRemoteFiles(String directory);
    /** 上传本地候选文件，FTP已存在同名文件时跳过。 */
    UploadResult syncMissingFiles(String ruleName,String remoteDirectory,List<Path> localFiles);
    /** 下载FTP最新N个候选文件，本地已存在同名文件时跳过。 */
    UploadResult syncMissingDownloads(String ruleName,String remoteDirectory,Path localDirectory,int latestFileCount);
}
