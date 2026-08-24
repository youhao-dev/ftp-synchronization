package cn.you.ftpsynchronization;

import javafx.application.Application;

/**
 * FTP 同步工具启动入口。该类不继承 JavaFX Application，便于生成可直接 java -jar 启动且不含 JavaFX 的发布 JAR。
 */
public class FtpSynchronizationApplication {
    /**
     * 启动 JavaFX 应用。
     */
    public static void main(String[] args) { Application.launch(FxStart.class,args); }
}
