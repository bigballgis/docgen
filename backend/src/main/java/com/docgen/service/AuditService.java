package com.docgen.service;

import com.docgen.entity.AuditLog;
import com.docgen.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 审计日志服务
 * 提供审计日志记录和查询功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /** 日期时间格式化器 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 记录审计日志
     * 创建一条审计日志记录并保存到数据库
     *
     * @param userId    操作用户ID
     * @param username  操作用户名
     * @param action    操作动作（如：LOGIN、LOGOUT、UPLOAD 等）
     * @param resource  资源类型（如：user、template、document 等）
     * @param resourceId 资源ID
     * @param ip        操作IP地址
     * @param userAgent 用户代理（浏览器信息）
     * @param details   操作详情（可选）
     */
    public void logAudit(Long userId, String username, String action, String resource,
                         String resourceId, String ip, String userAgent, String details) {
        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .username(username)
                .action(action)
                .resource(resource)
                .resourceId(resourceId)
                .ip(ip)
                .userAgent(userAgent)
                .details(details)
                .createdAt(LocalDateTime.now())
                .build();

        auditLogRepository.save(auditLog);
        log.debug("审计日志已记录: {} - {} - {}", username, action, resource);
    }

    /**
     * 分页查询审计日志
     * 支持按用户ID、操作动作、时间范围过滤
     *
     * @param userId    用户ID过滤（可选）
     * @param action    操作动作过滤（可选）
     * @param startDate 开始时间（可选，格式：yyyy-MM-dd）
     * @param endDate   结束时间（可选，格式：yyyy-MM-dd）
     * @param page      页码（从0开始）
     * @param size      每页大小
     * @return 审计日志分页列表
     */
    public Page<AuditLog> getAuditLogs(Long userId, String action, String startDate,
                                       String endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 处理空字符串参数，转换为 null 以匹配 @Query 中的 IS NULL 条件
        String act = (action != null && !action.isBlank()) ? action : null;
        LocalDateTime start = null;
        LocalDateTime end = null;

        if (startDate != null && !startDate.isBlank()) {
            start = LocalDateTime.parse(startDate + " 00:00:00", DATE_TIME_FORMATTER);
        }
        if (endDate != null && !endDate.isBlank()) {
            end = LocalDateTime.parse(endDate + " 23:59:59", DATE_TIME_FORMATTER);
        }

        // 使用数据库层面过滤的查询方法，一次性完成所有过滤和分页
        return auditLogRepository.findWithFilters(userId, act, start, end, pageable);
    }
}
