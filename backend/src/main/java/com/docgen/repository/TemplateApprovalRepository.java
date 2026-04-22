package com.docgen.repository;

import com.docgen.entity.TemplateApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 模板审批数据访问层
 */
public interface TemplateApprovalRepository extends JpaRepository<TemplateApproval, Long> {

    /**
     * 根据模板ID查询审批记录（按创建时间降序排列）
     *
     * @param templateId 模板ID
     * @return 审批记录列表
     */
    List<TemplateApproval> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
}
