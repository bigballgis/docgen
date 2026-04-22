package com.docgen.repository;

import com.docgen.entity.TemplateComposition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 模板组合关系数据访问层
 */
public interface TemplateCompositionRepository extends JpaRepository<TemplateComposition, Long> {

    /**
     * 根据模板ID查询组合关系（按排序顺序升序排列）
     *
     * @param templateId 模板ID
     * @return 组合关系列表
     */
    List<TemplateComposition> findByTemplateIdOrderBySortOrder(Long templateId);

    /**
     * 根据模板ID查询组合关系并关联片段（JOIN 查询，消除 N+1 问题）
     *
     * @param templateId 模板ID
     * @return Object[] 数组，[0] 为 TemplateComposition，[1] 为 Fragment（可能为 null）
     */
    @Query("SELECT tc, f FROM TemplateComposition tc LEFT JOIN Fragment f ON tc.fragmentId = f.id " +
           "WHERE tc.templateId = :templateId ORDER BY tc.sortOrder")
    List<Object[]> findWithFragments(@Param("templateId") Long templateId);

    /**
     * 根据模板ID删除所有组合关系
     *
     * @param templateId 模板ID
     */
    void deleteByTemplateId(Long templateId);

    /**
     * 根据模板ID和片段ID查询组合关系
     *
     * @param templateId 模板ID
     * @param fragmentId 片段ID
     * @return 组合关系信息
     */
    Optional<TemplateComposition> findByTemplateIdAndFragmentId(Long templateId, Long fragmentId);
}
