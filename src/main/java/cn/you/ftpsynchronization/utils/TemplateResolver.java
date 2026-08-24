package cn.you.ftpsynchronization.utils;

import cn.you.ftpsynchronization.pojo.FileInfo;

import java.time.LocalDateTime;

/**
 * 上传目录占位符解析器。规则：小写不补零、大写补零；选择补0规则小写也补零。
 */
public final class TemplateResolver {
    private TemplateResolver() {}

    /**
     * 根据规则版本解析动态路径。
     */
    public static String resolve(FileInfo rule, LocalDateTime time) { return resolve(rule==null?"":rule.getRulePath(),time,rule==null||rule.usesLegacyTemplate()); }

    /**
     * 根据指定兼容模式解析路径模板。
     */
    public static String resolve(String template,LocalDateTime time,boolean legacy) {
        String value=template==null?"":template;
        String year=String.valueOf(time.getYear()),month=String.valueOf(time.getMonthValue()),day=String.valueOf(time.getDayOfMonth()),hh=String.valueOf(time.getHour()),mm=String.valueOf(time.getMinute()),ss=String.valueOf(time.getSecond());
        String YEAR=String.format("%04d",time.getYear()),MONTH=String.format("%02d",time.getMonthValue()),DAY=String.format("%02d",time.getDayOfMonth()),HH=String.format("%02d",time.getHour()),MM=String.format("%02d",time.getMinute()),SS=String.format("%02d",time.getSecond());
        if (legacy) { year=YEAR; month=MONTH; day=DAY; hh=HH; mm=MM; ss=SS; }
        return value.replace("{year}",year).replace("{YEAR}",YEAR).replace("{month}",month).replace("{MONTH}",MONTH).replace("{day}",day).replace("{DAY}",DAY).replace("{hh}",hh).replace("{HH}",HH).replace("{mm}",mm).replace("{MM}",MM).replace("{ss}",ss).replace("{SS}",SS);
    }

    /**
     * 拼接用于界面预览的本地路径，不依赖当前运行系统的路径分隔符。
     */
    public static String joinLocalPreview(String base,String child) {
        String left=base==null?"":base.trim(),right=child==null?"":child.trim().replaceAll("^[\\\\/]+","");
        if (left.isBlank()) return right;
        char separator=left.contains("\\")?'\\':'/';
        while (left.endsWith("/")||left.endsWith("\\")) left=left.substring(0,left.length()-1);
        return right.isBlank()?left:left+separator+right.replace('/',separator).replace('\\',separator);
    }

    /**
     * 拼接 FTP 远程路径。
     */
    public static String joinRemote(String base,String child) {
        String left=base==null?"":base.trim().replace('\\','/').replaceAll("/+$",""),right=child==null?"":child.trim().replace('\\','/').replaceAll("^/+","");
        String result=(left+"/"+right).replaceAll("/{2,}","/");
        if (base!=null&&base.trim().startsWith("/")&&!result.startsWith("/")) result="/"+result;
        return result.isBlank()?"/":result;
    }
}
