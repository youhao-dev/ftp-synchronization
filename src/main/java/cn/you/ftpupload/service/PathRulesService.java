package cn.you.ftpupload.service;

import cn.you.ftpupload.pojo.FileInfo;

import java.util.Map;

/**
 * 上传规则业务接口。
 */
public interface PathRulesService {
    /** 获取规则。 */
    FileInfo getPath(String id);
    /** 获取全部规则。 */
    Map<String,FileInfo> getAllPath();
    /** 新增规则。 */
    FileInfo addPath(FileInfo fileInfo);
    /** 更新规则。 */
    void updatePath(FileInfo fileInfo);
    /** 删除规则。 */
    void deletePathById(String id);
    /** 清空全部规则。 */
    void clear();
}
