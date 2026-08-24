package cn.you.ftpsynchronization.i18n;

import java.util.Arrays;
import java.util.Locale;

/** 软件支持的界面语言。 */
public enum SupportedLanguage {
    AUTO("auto","跟随系统 / System"),
    ZH_CN("zh-CN","中文"),
    EN("en","English"),
    JA("ja","日本語"),
    KO("ko","한국어");

    private final String code;
    private final String displayName;

    /** 创建语言选项。 */
    SupportedLanguage(String code,String displayName) { this.code=code; this.displayName=displayName; }

    /** 获取持久化语言代码。 */
    public String code() { return code; }

    /** 把配置字符串转换为受支持语言。 */
    public static SupportedLanguage fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equalsIgnoreCase(code==null?"":code.trim())).findFirst().orElse(AUTO);
    }

    /** 获取当前选项实际使用的 Locale。 */
    public Locale resolveLocale() {
        Locale locale=this==AUTO?Locale.getDefault():Locale.forLanguageTag(code);
        return switch (locale.getLanguage()) {
            case "zh" -> Locale.SIMPLIFIED_CHINESE;
            case "ja" -> Locale.JAPANESE;
            case "ko" -> Locale.KOREAN;
            default -> Locale.ENGLISH;
        };
    }

    /** 下拉框始终用各语言本名显示，确保用户能找到自己的语言。 */
    @Override public String toString() { return displayName; }
}
