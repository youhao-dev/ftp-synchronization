package cn.you.ftpsynchronization.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;

/**
 * JSON 文件工具，专门用于兼容和安全保存原 pathRules.json。
 */
public final class JsonUtil {
    private static final ObjectMapper MAPPER=new ObjectMapper();

    static {
        MAPPER.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,false);
        MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS,false);
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS);
        MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    }

    private JsonUtil() {}

    /**
     * 从 JSON 文件读取对象。
     */
    public static <T> T read(Path file,TypeReference<T> type) throws IOException { return MAPPER.readValue(file.toFile(),type); }

    /**
     * 先写临时文件再替换正式文件，降低配置文件因异常退出而损坏的概率。
     */
    public static void writeAtomic(Path file,Object value) throws IOException {
        Path absolute=file.toAbsolutePath();
        Path parent=absolute.getParent();
        if (parent!=null) Files.createDirectories(parent);
        Path temp=absolute.resolveSibling(absolute.getFileName()+".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),value);
        try { Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING); }
    }
}
