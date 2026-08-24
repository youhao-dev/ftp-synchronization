package cn.you.ftpsynchronization.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 动态目录模板测试。 */
class TemplateResolverTest {
    /** 验证新版模板大小写补零规则。 */
    @Test void resolvesModernTokens() {
        LocalDateTime time=LocalDateTime.of(2026,8,1,3,5,9);
        assertEquals("2026/8/01/3/05/9/09",TemplateResolver.resolve("{year}/{month}/{DAY}/{hh}/{MM}/{ss}/{SS}",time,false));
    }

    /** 验证远程路径拼接统一使用斜杠。 */
    @Test void joinsRemotePaths() { assertEquals("/root/2026/08/file.txt",TemplateResolver.joinRemote("/root/","/2026/08/file.txt")); }
}
