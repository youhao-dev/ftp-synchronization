package cn.you.ftpsynchronization.view;

import cn.you.ftpsynchronization.AppContext;
import cn.you.ftpsynchronization.FxStart;
import cn.you.ftpsynchronization.i18n.I18n;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 四语 FXML 精确文案和加载冒烟测试。 */
class FxmlLocalizationTest {
    /** 启动一次 JavaFX 工具包供全部测试复用。 */
    @BeforeAll static void startJavaFx() throws Exception {
        CountDownLatch started=new CountDownLatch(1);
        try { Platform.startup(started::countDown); } catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10,TimeUnit.SECONDS));
    }

    /** 测试完成后退出 JavaFX 工具包。 */
    @AfterAll static void stopJavaFx() { Platform.exit(); }

    /** 验证四种语言的主窗口和规则窗口显示准确文案。 */
    @Test void loadsExactTextsFromBothFxmlFilesForEveryLanguage() throws Exception {
        AppContext.getInstance();
        for (var sample:List.of(
                new LanguageSample("zh-CN","传输规则","新增传输规则","例如：雷达图片上传"),
                new LanguageSample("en","Transfer Rules","Add Transfer Rule","Example: Radar image upload"),
                new LanguageSample("ja","転送ルール","転送ルールを追加","例: レーダー画像アップロード"),
                new LanguageSample("ko","전송 규칙","전송 규칙 추가","예: 레이더 이미지 업로드")))
            runOnFxThread(() -> { assertFxml(sample); return null; });
    }

    /** 加载两个 FXML 并断言代表性静态文案。 */
    private void assertFxml(LanguageSample sample) throws Exception {
        I18n.configure(sample.code());
        FXMLLoader mainLoader=new FXMLLoader(FxStart.class.getResource("/fxml/index.fxml"),I18n.bundle());
        mainLoader.load();
        assertEquals(sample.rules(),((Labeled)mainLoader.getNamespace().get("navRulesButton")).getText());
        ((IndexController)mainLoader.getController()).dispose();

        FXMLLoader addLoader=new FXMLLoader(FxStart.class.getResource("/fxml/add.fxml"),I18n.bundle());
        addLoader.load();
        assertEquals(sample.dialogTitle(),((Labeled)addLoader.getNamespace().get("dialogTitle")).getText());
        assertEquals(sample.prompt(),((TextField)addLoader.getNamespace().get("name")).getPromptText());
    }

    /** 在 JavaFX 线程执行测试动作并传播异常。 */
    private static <T> T runOnFxThread(ThrowingSupplier<T> action) throws Exception {
        CountDownLatch completed=new CountDownLatch(1);
        AtomicReference<T> result=new AtomicReference<>();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Platform.runLater(() -> { try { result.set(action.get()); } catch (Throwable e) { failure.set(e); } finally { completed.countDown(); } });
        assertTrue(completed.await(20,TimeUnit.SECONDS),"JavaFX action timed out");
        if (failure.get()!=null) throw new AssertionError(failure.get());
        return result.get();
    }

    /** 界面精确文案测试样本。 */
    private record LanguageSample(String code,String rules,String dialogTitle,String prompt) {}

    /** 允许测试辅助方法传播受检异常。 */
    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }
}
