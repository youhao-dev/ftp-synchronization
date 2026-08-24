package cn.you.ftpupload;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * JavaFX 生命周期入口。
 */
public class FxStart extends Application {
    private final AppContext appContext=AppContext.getInstance();

    /**
     * 创建主窗口并启动定时上传任务。
     */
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader=new FXMLLoader(FxStart.class.getResource("/fxml/index.fxml"));
        Rectangle2D visual=Screen.getPrimary().getVisualBounds();
        double width=Math.max(900,Math.min(1280,visual.getWidth()-60)),height=Math.max(600,Math.min(800,visual.getHeight()-60));
        Scene scene=new Scene(loader.load(),width,height);
        scene.getStylesheets().add(FxStart.class.getResource("/css/app.css").toExternalForm());
        stage.setTitle("FTP 定时上传工具");
        stage.setMinWidth(Math.min(980,width)); stage.setMinHeight(Math.min(620,height)); stage.setScene(scene); stage.show();
        appContext.logStore().info("JavaFX 主界面已启动");
        appContext.uploadStack().start();
    }

    /**
     * 关闭后台调度线程、虚拟线程和 FTP 连接池。
     */
    @Override
    public void stop() { appContext.logStore().info("程序正在退出"); appContext.shutdown(); }
}
