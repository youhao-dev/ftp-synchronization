package cn.you.ftpsynchronization.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 旧规则 JSON 兼容性测试。 */
class FileInfoCompatibilityTest {
    /** 验证缺少方向和模板版本的旧规则仍按上传及旧模板处理。 */
    @Test void legacyJsonDefaultsToUpload() throws Exception {
        FileInfo rule=new ObjectMapper().readValue("{\"id\":\"1\",\"name\":\"legacy\",\"basePath\":\"C:/data\",\"rulePath\":\"{DAY}\",\"remoteBasePath\":\"/data\"}",FileInfo.class);
        assertTrue(rule.isUpload());
        assertTrue(rule.usesLegacyTemplate());
        assertTrue(rule.getIsRunning());
    }

    /** 验证下载方向仍使用稳定代码序列化。 */
    @Test void downloadDirectionRoundTrips() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        FileInfo source=new FileInfo().setTransferType(FileInfo.TRANSFER_DOWNLOAD);
        FileInfo restored=mapper.readValue(mapper.writeValueAsString(source),FileInfo.class);
        assertTrue(restored.isDownload());
    }
}
