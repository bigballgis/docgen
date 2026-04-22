package com.docgen.repository;

import com.docgen.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 审计日志数据访问层
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 带动态条件查询审计日志（数据库层面过滤，避免双重分页问题）
     *
     * @param userId    用户ID过滤（可选）
     * @param action    操作动作过滤（可选）
     * @param startDate 开始时间（可选）
     * @param endDate   结束时间（可选）
     * @param pageable  分页参数
     * @return 审计日志分页列表
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) " +
           "AND (:action IS NULL OR a.action = :action) " +
           "AND (:startDate IS NULL OR a.createdAt >= :startDate) " +
           "AND (:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findWithFilters(@Param("userId") Long userId,
                                   @Param("action") String action,
                                   @Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate,
                                   Pageable pageable);

    /**
     * 根据用户ID查询审计日志（分页）
     *
     * @param userId   用户ID
     * @param pageable 分页参数
     * @return 审计日志分页列表
     */
    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据操作动作查询审计日志（分页）
     *
     * @param action   操作动作
     * @param pageable 分页参数
     * @return 审计日志分页列表
     */
    Page<AuditLog> findByAction(String action, Pageable pageable);

    /**
     * 统计指定操作动作的日志数量
     *
     * @param action 操作动作
     * @return 日志数量
     */
    long countByAction(String action);
}
