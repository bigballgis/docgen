package com.docgen.repository;

import com.docgen.entity.Template;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 模板数据访问层
 */
public interface TemplateRepository extends JpaRepository<Template, Long> {

    /**
     * 带动态条件查询模板（数据库层面过滤，避免全表扫描+内存过滤）
     *
     * @param tenantId 租户ID
     * @param keyword  搜索关键字（匹配名称和描述）
     * @param category 分类过滤
     * @param status   状态过滤
     * @param pageable 分页参数
     * @return 模板分页列表
     */
    @Query("SELECT t FROM Template t WHERE t.tenantId = :tenantId " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR t.category = :category) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND t.deletedAt IS NULL " +
           "ORDER BY t.createTime DESC")
    Page<Template> findWithFilters(@Param("tenantId") String tenantId,
                                   @Param("keyword") String keyword,
                                   @Param("category") String category,
                                   @Param("status") String status,
                                   Pageable pageable);

    /**
     * 查询未软删除且状态为 pending 的模板（数据库层面过滤）
     *
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 待审批模板分页列表
     */
    @Query("SELECT t FROM Template t WHERE t.tenantId = :tenantId AND t.status = 'pending' AND t.deletedAt IS NULL ORDER BY t.createTime DESC")
    Page<Template> findPendingTemplates(@Param("tenantId") String tenantId, Pageable pageable);

    /**
     * 查询未软删除且有分类的模板（用于获取分类列表）
     *
     * @param tenantId 租户ID
     * @return 有分类的模板列表
     */
    @Query("SELECT DISTINCT t.category FROM Template t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL AND t.category IS NOT NULL AND t.category <> ''")
    List<String> findDistinctCategories(@Param("tenantId") String tenantId);

    /**
     * 查询未软删除的模板（用于导出）
     *
     * @param tenantId 租户ID
     * @return 模板列表
     */
    @Query("SELECT t FROM Template t WHERE t.tenantId = :tenantId AND t.deletedAt IS NULL ORDER BY t.createTime DESC")
    List<Template> findAllActiveByTenantId(@Param("tenantId") String tenantId);

    /**
     * 根据租户ID查询非指定状态的模板（分页）
     *
     * @param tenantId 租户ID
     * @param status   要排除的状态
     * @param pageable 分页参数
     * @return 模板分页列表
     */
    Page<Template> findByTenantIdAndStatusNot(String tenantId, String status, Pageable pageable);

    /**
     * 根据租户ID和状态查询模板（分页）
     *
     * @param tenantId 租户ID
     * @param status   模板状态
     * @param pageable 分页参数
     * @return 模板分页列表
     */
    Page<Template> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);

    /**
     * 根据租户ID查询模板（分页）
     *
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 模板分页列表
     */
    Page<Template> findByTenantId(String tenantId, Pageable pageable);

    /**
     * 根据状态查询模板列表
     *
     * @param status 模板状态
     * @return 模板列表
     */
    List<Template> findByStatus(String status);

    /**
     * 根据状态和租户ID查询未软删除的模板列表（按创建时间降序）
     *
     * @param status   模板状态
     * @param tenantId 租户ID
     * @return 模板列表
     */
    @Query("SELECT t FROM Template t WHERE t.status = :status AND t.deletedAt IS NULL AND t.tenantId = :tenantId ORDER BY t.createTime DESC")
    List<Template> findByStatusAndTenantId(@Param("status") String status, @Param("tenantId") String tenantId);

    /**
     * 根据租户ID查询有分类的模板列表
     *
     * @param tenantId 租户ID
     * @return 有分类的模板列表
     */
    List<Template> findByTenantIdAndCategoryIsNotNull(String tenantId);

    /**
     * 统计指定租户下某状态的模板数量
     *
     * @param tenantId 租户ID
     * @param status   模板状态
     * @return 模板数量
     */
    long countByTenantIdAndStatus(String tenantId, String status);

    /**
     * 统计指定租户下的模板总数
     *
     * @param tenantId 租户ID
     * @return 模板数量
     */
    long countByTenantId(String tenantId);
}
