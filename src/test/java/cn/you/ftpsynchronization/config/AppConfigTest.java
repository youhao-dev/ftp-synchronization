package cn.you.ftpsynchronization.config;

import cn.you.ftpsynchronization.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 配置文件迁移和优先级测试。 */
class AppConfigTest {
    @TempDir Path tempDirectory;

    /** 验证旧配置被读取并复制到新文件，且旧文件保留。 */
    @Test void migratesLegacySettingsWithoutDeletingSource() throws Exception {
        Path legacy=tempDirectory.resolve("ftp-upload-settings.properties");
        Files.writeString(legacy,"ftp.host=legacy.example\nftp.port=2121\nui.language=ja\n");
        AppConfig config=new AppConfig(tempDirectory);
        assertEquals("legacy.example",config.ftpHost());
        assertEquals(2121,config.ftpPort());
        assertEquals("ja",config.language());
        assertTrue(Files.isRegularFile(config.settingsFile()));
        assertTrue(Files.isRegularFile(legacy));
    }

    /** 验证新配置存在时优先于旧配置。 */
    @Test void newSettingsTakePriorityOverLegacySettings() throws Exception {
        Files.writeString(tempDirectory.resolve("ftp-upload-settings.properties"),"ftp.host=legacy.example\n");
        Files.writeString(tempDirectory.resolve("ftp-synchronization-settings.properties"),"ftp.host=new.example\nui.language=ko\n");
        AppConfig config=new AppConfig(tempDirectory);
        assertEquals("new.example",config.ftpHost());
        assertEquals("ko",config.language());
    }

    /** 验证即时保存语言不会丢失其它运行设置。 */
    @Test void savesLanguageAlongsideRuntimeSettings() throws Exception {
        AppConfig config=new AppConfig(tempDirectory);
        config.saveLanguage("en");
        AppConfig reloaded=new AppConfig(tempDirectory);
        assertEquals("en",reloaded.language());
        assertEquals(21,reloaded.ftpPort());
        I18n.configure(reloaded.language());
        assertEquals("Transfer Rules",I18n.text("nav.rules"));
    }
}
