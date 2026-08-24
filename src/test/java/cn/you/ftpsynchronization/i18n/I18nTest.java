package cn.you.ftpsynchronization.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 标准 properties 国际化资源和 Locale 回退测试。 */
class I18nTest {
    private final Locale originalLocale=Locale.getDefault();

    /** 每个测试后恢复 JVM Locale 和跟随系统选项。 */
    @AfterEach void restoreLanguage() { Locale.setDefault(originalLocale); I18n.configure("auto"); }

    /** 验证四种显式语言使用准确文案。 */
    @Test void loadsExactMessagesForEveryExplicitLanguage() {
        assertLanguage("zh-CN","传输规则","例如：雷达图片上传");
        assertLanguage("en","Transfer Rules","Example: Radar image upload");
        assertLanguage("ja","転送ルール","例: レーダー画像アップロード");
        assertLanguage("ko","전송 규칙","예: 레이더 이미지 업로드");
    }

    /** 验证显式英文不会因 JVM 默认中文而回退中文。 */
    @Test void explicitEnglishIgnoresChineseJvmLocale() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        I18n.configure("en");
        assertEquals(Locale.ENGLISH,I18n.locale());
        assertEquals("Transfer Rules",I18n.text("nav.rules"));
    }

    /** 验证跟随系统支持四种语言，不支持语言回退英文。 */
    @Test void autoLanguageMapsSupportedLocalesAndFallsBackToEnglish() {
        for (var sample:List.of(
                new LocaleSample(Locale.SIMPLIFIED_CHINESE,"传输规则"),
                new LocaleSample(Locale.ENGLISH,"Transfer Rules"),
                new LocaleSample(Locale.JAPANESE,"転送ルール"),
                new LocaleSample(Locale.KOREAN,"전송 규칙"),
                new LocaleSample(Locale.FRENCH,"Transfer Rules"))) {
            Locale.setDefault(sample.locale());
            I18n.configure("auto");
            assertEquals(sample.expected(),I18n.text("nav.rules"));
        }
    }

    /** 验证四个资源文件的键集完全一致。 */
    @Test void propertyBundlesHaveIdenticalKeys() throws Exception {
        Set<String> expected=properties("messages.properties").stringPropertyNames();
        assertFalse(expected.isEmpty());
        for (String file:List.of("messages_zh_CN.properties","messages_ja.properties","messages_ko.properties"))
            assertEquals(expected,properties(file).stringPropertyNames(),file);
    }

    /** 验证 FXML 只使用存在的资源键，且不再包含中文静态属性。 */
    @Test void fxmlUsesValidResourceKeysWithoutChineseLiterals() throws Exception {
        Set<String> keys=properties("messages.properties").stringPropertyNames();
        Pattern reference=Pattern.compile("(?:text|promptText)=\\\"%([^\\\"]+)\\\"");
        Pattern literal=Pattern.compile("(?:text|promptText)=\\\"([^\\\"]+)\\\"");
        for (String resource:List.of("/fxml/index.fxml","/fxml/add.fxml")) {
            String fxml;
            try (var input=I18nTest.class.getResourceAsStream(resource)) {
                assertNotNull(input,resource);
                fxml=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            }
            var references=reference.matcher(fxml);
            Set<String> used=new LinkedHashSet<>();
            while (references.find()) { used.add(references.group(1)); assertTrue(keys.contains(references.group(1)),"missing key: "+references.group(1)); }
            assertFalse(used.isEmpty(),resource);
            var literals=literal.matcher(fxml);
            while (literals.find()) assertFalse(containsHan(literals.group(1)),"hard-coded Chinese text: "+literals.group(1));
        }
    }

    /** 断言指定语言的代表性资源。 */
    private void assertLanguage(String code,String rules,String prompt) {
        I18n.configure(code);
        assertEquals(rules,I18n.text("nav.rules"));
        assertEquals(prompt,I18n.text("prompt.ruleName"));
    }

    /** 使用 UTF-8 Reader 读取指定 properties 资源。 */
    private Properties properties(String filename) throws Exception {
        Properties properties=new Properties();
        try (var input=I18nTest.class.getResourceAsStream("/i18n/"+filename)) {
            assertNotNull(input,filename);
            properties.load(new InputStreamReader(input,StandardCharsets.UTF_8));
        }
        return properties;
    }

    /** 检查文本是否包含汉字。 */
    private boolean containsHan(String text) {
        return text.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint)==Character.UnicodeScript.HAN);
    }

    /** 跟随系统 Locale 测试样本。 */
    private record LocaleSample(Locale locale,String expected) {}
}
