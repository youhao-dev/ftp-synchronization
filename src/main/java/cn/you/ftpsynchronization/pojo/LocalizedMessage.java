package cn.you.ftpsynchronization.pojo;

import cn.you.ftpsynchronization.i18n.I18n;

import java.util.Arrays;

/**
 * 可重新渲染的界面消息；内部文案保存资源键，外部诊断文本保留原文。
 */
public record LocalizedMessage(String resourceKey,Object[] arguments,String rawText) {
    /** 创建消息快照并防止外部修改参数数组。 */
    public LocalizedMessage {
        arguments=arguments==null?new Object[0]:Arrays.copyOf(arguments,arguments.length);
        rawText=rawText==null?"":rawText;
    }

    /** 创建可随语言重新渲染的内部消息。 */
    public static LocalizedMessage localized(String resourceKey,Object... arguments) {
        return new LocalizedMessage(resourceKey,arguments,"");
    }

    /** 创建不翻译的服务器响应或异常原文。 */
    public static LocalizedMessage raw(String rawText) {
        return new LocalizedMessage(null,new Object[0],rawText);
    }

    /** 返回参数副本，避免记录内容被修改。 */
    @Override public Object[] arguments() { return Arrays.copyOf(arguments,arguments.length); }

    /** 按当前应用语言渲染消息。 */
    public String render() {
        return resourceKey==null||resourceKey.isBlank()?rawText:I18n.text(resourceKey,arguments);
    }

    /** MessageFormat 遇到嵌套消息参数时使用当前语言渲染。 */
    @Override public String toString() { return render(); }
}
