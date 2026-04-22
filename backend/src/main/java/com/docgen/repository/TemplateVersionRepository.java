package com.docgen.repository;

import com.docgen.entity.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 模板版本数据访问层
 */
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    /**
     * 根据模板ID查询所有版本（按版本号降序排列）
     *
     * @param templateId 模板ID
     * @return 版本列表
     */
    List<TemplateVersion> findByTemplateIdOrderByVersionDesc(Long templateId);

    /**
     * 根据模板ID和版本号查询指定版本
     *
     * @param templateId 模板ID
     * @param version    版本号
     * @return 模板版本信息
     */
    Optional<TemplateVersion> findByTemplateIdAndVersion(Long templateId, Integer version);

    /**
     * 查询模板的最新版本
     *
     * @param templateId 模板ID
     * @return 最新版本信息
     */
    Optional<TemplateVersion> findTopByTemplateIdOrderByVersionDesc(Long templateId);
}
