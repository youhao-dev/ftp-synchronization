package cn.you.ftpsynchronization.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** 统一管理标准 properties 资源包和应用内语言选择。 */
public final class I18n {
    private static final String BUNDLE_NAME="i18n.messages";
    private static final ResourceBundle.Control CONTROL=ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static volatile SupportedLanguage selectedLanguage=SupportedLanguage.AUTO;
    private static volatile Locale activeLocale=selectedLanguage.resolveLocale();
    private static volatile ResourceBundle bundle=load(activeLocale);

    /** 工具类不允许实例化。 */
    private I18n() {}

    /** 应用持久化语言设置。 */
    public static synchronized void configure(String code) {
        selectedLanguage=SupportedLanguage.fromCode(code);
        activeLocale=selectedLanguage.resolveLocale();
        bundle=load(activeLocale);
    }

    /** 获取当前语言选项。 */
    public static SupportedLanguage language() { return selectedLanguage; }

    /** 获取当前应用实际使用的 Locale，不依赖 JVM 默认 Locale 回退。 */
    public static Locale locale() { return activeLocale; }

    /** 获取当前 FXMLLoader 使用的资源包。 */
    public static ResourceBundle bundle() { return bundle; }

    /** 翻译资源键并按当前语言格式化参数。 */
    public static String text(String key,Object... arguments) {
        String pattern;
        try { pattern=bundle.getString(key); }
        catch (MissingResourceException e) { pattern=key; }
        return arguments==null||arguments.length==0?pattern:new MessageFormat(pattern,locale()).format(arguments);
    }

    /** 仅从 UTF-8 properties 加载 Locale 对应资源，缺失时回退英文基础包。 */
    private static ResourceBundle load(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE_NAME,locale,I18n.class.getClassLoader(),CONTROL);
    }
}
