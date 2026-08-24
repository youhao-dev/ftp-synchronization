package cn.you.ftpupload;

import javafx.application.Application;

/**
 * FTP 定时上传工具启动入口。该类不继承 JavaFX Application，便于 Maven Shade 生成可直接 java -jar 启动的胖 JAR。
 */
public class FtpUploadApplication {
    /**
     * 启动 JavaFX 应用。
     */
    public static void main(String[] args) { Application.launch(FxStart.class,args); }
}
