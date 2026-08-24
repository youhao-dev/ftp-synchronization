package cn.you.ftpupload.service.impl;

import cn.you.ftpupload.pojo.FileInfo;
import cn.you.ftpupload.service.PathRules;
import cn.you.ftpupload.service.PathRulesService;

import java.util.Map;
import java.util.UUID;

/**
 * 上传规则业务实现。
 */
public class PathRulesServiceImpl implements PathRulesService {
    private final PathRules pathRules;

    /** 创建规则业务服务。 */
    public PathRulesServiceImpl(PathRules pathRules) { this.pathRules=pathRules; }

    /** 获取规则。 */
    @Override public FileInfo getPath(String id) { return pathRules.getPath(id); }
    /** 获取全部规则。 */
    @Override public Map<String,FileInfo> getAllPath() { return pathRules.getAllPath(); }

    /**
     * 新增规则并生成 32 位 UUID ID。
     */
    @Override
    public FileInfo addPath(FileInfo fileInfo) {
        String id=UUID.randomUUID().toString().replace("-","");
        pathRules.setPath(id,fileInfo);
        return fileInfo;
    }

    /**
     * 更新规则，保持原 ID 不变。
     */
    @Override
    public void updatePath(FileInfo fileInfo) {
        if (fileInfo.getId()==null||fileInfo.getId().isBlank()) throw new IllegalArgumentException("规则 ID 不能为空");
        pathRules.setPath(fileInfo.getId(),fileInfo);
    }

    /** 删除规则。 */
    @Override public void deletePathById(String id) { pathRules.remove(id); }
    /** 清空规则。 */
    @Override public void clear() { pathRules.clear(); }
}
