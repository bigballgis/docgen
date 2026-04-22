package com.docgen.service;

import com.docgen.entity.Template;
import com.docgen.entity.TemplateApproval;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.BusinessException;
import com.docgen.repository.TemplateApprovalRepository;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批流服务
 * 对应 Node.js 的 approvalService.js
 * 负责模板的提交审批、通过、驳回等审批流程操作
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    private final TemplateRepository templateRepository;
    private final TemplateApprovalRepository templateApprovalRepository;
    private final TemplateVersionRepository templateVersionRepository;

    /**
     * 提交模板审批
     * 只有草稿（draft）或已驳回（rejected）状态的模板才能提交审批
     *
     * @param templateId 模板ID
     * @param userId     提交人用户ID
     * @return 更新后的模板信息
     */
    @Transactional
    public Template submitForApproval(Long templateId, Long userId) {
        log.info("提交模板审批, templateId={}, userId={}", templateId, userId);

        // 查询模板
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 检查模板状态必须是 draft 或 rejected
        if (!"draft".equals(template.getStatus()) && !"rejected".equals(template.getStatus())) {
            throw new BusinessException("只有草稿或已驳回状态的模板才能提交审批，当前状态：" + template.getStatus());
        }

        // 更新状态为 pending
        template.setStatus("pending");
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        // 创建审批记录（action="submit"）
        TemplateApproval approval = TemplateApproval.builder()
                .templateId(templateId)
                .action("submit")
                .reviewerId(userId)
                .comment("提交审批")
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
        templateApprovalRepository.save(approval);

        log.info("模板审批提交成功, templateId={}", templateId);
        return template;
    }

    /**
     * 审批通过模板
     * 只有待审批（pending）状态的模板才能被审批通过
     *
     * @param templateId 模板ID
     * @param reviewerId 审批人用户ID
     * @param comment    审批意见
     * @return 更新后的模板信息
     */
    @Transactional
    public Template approveTemplate(Long templateId, Long reviewerId, String comment) {
        log.info("审批通过模板, templateId={}, reviewerId={}", templateId, reviewerId);

        // 查询模板
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 检查模板状态必须是 pending
        if (!"pending".equals(template.getStatus())) {
            throw new BusinessException("只有待审批状态的模板才能被审批通过，当前状态：" + template.getStatus());
        }

        // 更新状态为 published，version+1，设置发布信息和审批信息
        template.setStatus("published");
        template.setVersion(template.getVersion() + 1);
        template.setCurrentVersion(template.getVersion());
        template.setPublishedAt(LocalDateTime.now());
        template.setApprovedBy(reviewerId);
        template.setApprovedAt(LocalDateTime.now());
        template.setRejectReason(null);
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        // 创建审批记录（action="approve"）
        TemplateApproval approval = TemplateApproval.builder()
                .templateId(templateId)
                .action("approve")
                .reviewerId(reviewerId)
                .comment(comment)
                .status("approved")
                .createdAt(LocalDateTime.now())
                .build();
        templateApprovalRepository.save(approval);

        log.info("模板审批通过, templateId={}, newVersion={}", templateId, template.getVersion());
        return template;
    }

    /**
     * 驳回模板
     * 只有待审批（pending）状态的模板才能被驳回
     *
     * @param templateId 模板ID
     * @param reviewerId 审批人用户ID
     * @param reason     驳回原因
     * @return 更新后的模板信息
     */
    @Transactional
    public Template rejectTemplate(Long templateId, Long reviewerId, String reason) {
        log.info("驳回模板, templateId={}, reviewerId={}", templateId, reviewerId);

        // 查询模板
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 检查模板状态必须是 pending
        if (!"pending".equals(template.getStatus())) {
            throw new BusinessException("只有待审批状态的模板才能被驳回，当前状态：" + template.getStatus());
        }

        // 更新状态为 rejected，设置驳回原因
        template.setStatus("rejected");
        template.setRejectReason(reason);
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        // 创建审批记录（action="reject"）
        TemplateApproval approval = TemplateApproval.builder()
                .templateId(templateId)
                .action("reject")
                .reviewerId(reviewerId)
                .comment(reason)
                .status("rejected")
                .createdAt(LocalDateTime.now())
                .build();
        templateApprovalRepository.save(approval);

        log.info("模板已驳回, templateId={}, reason={}", templateId, reason);
        return template;
    }

    /**
     * 获取待审批模板列表
     * 根据租户ID过滤，返回所有状态为 pending 的模板
     *
     * @param tenantId 租户ID
     * @return 待审批模板列表
     */
    public List<Template> getPendingApprovals(String tenantId) {
        log.info("获取待审批模板列表, tenantId={}", tenantId);

        List<Template> pendingTemplates;
        if (tenantId != null && !tenantId.isEmpty()) {
            pendingTemplates = templateRepository.findByStatusAndTenantId("pending", tenantId);
        } else {
            pendingTemplates = templateRepository.findByStatus("pending");
        }

        log.info("待审批模板数量: {}", pendingTemplates.size());
        return pendingTemplates;
    }

    /**
     * 获取审批历史
     * 返回指定模板的所有审批记录，按时间降序排列
     *
     * @param templateId 模板ID
     * @return 审批记录列表
     */
    public List<TemplateApproval> getApprovalHistory(Long templateId) {
        log.info("获取审批历史, templateId={}", templateId);

        List<TemplateApproval> history = templateApprovalRepository.findByTemplateIdOrderByCreatedAtDesc(templateId);

        log.info("审批历史记录数量: {}", history.size());
        return history;
    }
}
