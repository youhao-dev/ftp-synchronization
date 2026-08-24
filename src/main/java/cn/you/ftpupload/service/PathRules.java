package cn.you.ftpupload.service;

import cn.you.ftpupload.pojo.FileInfo;
import cn.you.ftpupload.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 上传规则存储，工作目录下的 pathRules.json。
 */
public class PathRules {
    private static final Logger LOG=Logger.getLogger(PathRules.class.getName());
    private final Path file=Path.of("pathRules.json");
    private final Map<String, FileInfo> rules=new LinkedHashMap<>();

    /** 创建规则存储并加载 pathRules.json。 */
    public PathRules() { load(); }

    /**
     * 获取指定规则。
     */
    public synchronized FileInfo getPath(String id) { return rules.get(id); }

    /**
     * 获取规则快照，避免后台上传与界面修改相互干扰。
     */
    public synchronized Map<String,FileInfo> getAllPath() { return new LinkedHashMap<>(rules); }

    /**
     * 新增或覆盖规则并立即持久化。
     */
    public synchronized void setPath(String id,FileInfo path) { path.setId(id); rules.put(id,path); persist(); }

    /**
     * 删除规则并立即持久化。
     */
    public synchronized void remove(String id) { rules.remove(id); persist(); }

    /**
     * 清空规则并立即持久化。
     */
    public synchronized void clear() { rules.clear(); persist(); }

    /**
     * 启动时加载 JSON 配置。
     */
    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            Map<String,FileInfo> data= JsonUtil.read(file,new TypeReference<>() {});
            if (data!=null) rules.putAll(data);
        } catch (Exception e) { LOG.log(Level.SEVERE,"读取 pathRules.json 失败，已保留原文件不覆盖",e); }
    }

    /**
     * 安全保存规则配置。
     */
    private void persist() {
        try { JsonUtil.writeAtomic(file,rules); }
        catch (IOException e) { throw new IllegalStateException("保存 pathRules.json 失败",e); }
    }
}
