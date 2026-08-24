package cn.you.ftpsynchronization;

import cn.you.ftpsynchronization.i18n.I18n;
import cn.you.ftpsynchronization.pojo.LocalizedMessage;
import cn.you.ftpsynchronization.view.IndexController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * JavaFX 生命周期入口。
 */
public class FxStart extends Application {
    private final AppContext appContext=AppContext.getInstance();
    private static Stage primaryStage;
    private static IndexController activeController;

    /**
     * 创建主窗口并启动定时上传任务。
     */
    @Override
    public void start(Stage stage) throws Exception {
        Rectangle2D visual=Screen.getPrimary().getVisualBounds();
        double width=Math.max(900,Math.min(1280,visual.getWidth()-60)),height=Math.max(600,Math.min(800,visual.getHeight()-60));
        primaryStage=stage;
        stage.setWidth(width); stage.setHeight(height);
        stage.setMinWidth(Math.min(980,width)); stage.setMinHeight(Math.min(620,height));
        reloadMainView();
        stage.show();
        appContext.logStore().info(LocalizedMessage.localized("log.uiStarted"));
        appContext.uploadStack().start();
    }

    /** 重新加载主场景以立即应用新的资源包，同时保留后台任务。 */
    public static void reloadMainView() {
        if (primaryStage==null) return;
        try {
            IndexController.MainPage page=activeController==null?IndexController.MainPage.RULES:activeController.currentPage();
            if (activeController!=null) activeController.dispose();
            FXMLLoader loader=new FXMLLoader(FxStart.class.getResource("/fxml/index.fxml"),I18n.bundle());
            Parent root=loader.load();
            activeController=loader.getController();
            activeController.restorePage(page);
            Scene scene=new Scene(root);
            scene.getStylesheets().add(FxStart.class.getResource("/css/app.css").toExternalForm());
            primaryStage.setTitle(I18n.text("app.title"));
            primaryStage.setScene(scene);
        } catch (Exception e) { throw new IllegalStateException("Unable to reload interface",e); }
    }

    /**
     * 关闭后台调度线程、虚拟线程和 FTP 连接池。
     */
    @Override
    public void stop() { if (activeController!=null) activeController.dispose(); appContext.logStore().info(LocalizedMessage.localized("log.exiting")); appContext.shutdown(); }
}
