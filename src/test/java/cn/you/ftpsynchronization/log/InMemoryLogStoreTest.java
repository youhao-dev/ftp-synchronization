package cn.you.ftpsynchronization.log;

import cn.you.ftpsynchronization.i18n.I18n;
import cn.you.ftpsynchronization.pojo.FtpLogEntry;
import cn.you.ftpsynchronization.pojo.LocalizedMessage;
import cn.you.ftpsynchronization.pojo.TransferStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 日志监听器生命周期测试。 */
class InMemoryLogStoreTest {
    /** 验证注销动作会阻止旧界面继续接收日志。 */
    @Test void unsubscribeStopsNotifications() {
        InMemoryLogStore store=new InMemoryLogStore();
        AtomicInteger calls=new AtomicInteger();
        Runnable unsubscribe=store.addSystemListener(entry -> calls.incrementAndGet());
        store.info(LocalizedMessage.raw("first"));
        unsubscribe.run();
        store.info(LocalizedMessage.raw("second"));
        assertEquals(1,calls.get());
    }

    /** 验证历史内部消息可重新渲染，服务器原文保持不变。 */
    @Test void historicalMessagesRenderInCurrentLanguageWhileRawTextIsStable() {
        InMemoryLogStore store=new InMemoryLogStore();
        LocalizedMessage internal=LocalizedMessage.localized("ftp.uploadSuccess");
        LocalizedMessage raw=LocalizedMessage.raw("550 Permission denied");
        store.info(internal);
        store.addFtp(new FtpLogEntry(LocalDateTime.now(),"rule","file","local","remote",TransferStatus.FAILED,0,0,raw));
        I18n.configure("zh-CN");
        assertEquals("上传成功",store.systemSnapshot().getFirst().message().render());
        I18n.configure("en");
        assertEquals("Upload succeeded",store.systemSnapshot().getFirst().message().render());
        assertEquals("550 Permission denied",store.ftpSnapshot().getFirst().message().render());
    }
}
