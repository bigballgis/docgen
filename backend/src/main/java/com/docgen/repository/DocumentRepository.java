package com.docgen.repository;

import com.docgen.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 文档数据访问层
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * 带动态条件查询文档（数据库层面过滤，避免全表扫描+内存过滤）
     *
     * @param tenantId 租户ID
     * @param keyword  搜索关键字（匹配文件名和模板名）
     * @param status   状态过滤
     * @param pageable 分页参数
     * @return 文档分页列表
     */
    @Query("SELECT d FROM Document d WHERE d.deletedAt IS NULL " +
           "AND (:tenantId IS NULL OR d.tenantId = :tenantId) " +
           "AND (:keyword IS NULL OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.templateName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR d.status = :status) " +
           "ORDER BY d.createdAt DESC")
    Page<Document> findWithFilters(@Param("tenantId") String tenantId,
                                   @Param("keyword") String keyword,
                                   @Param("status") String status,
                                   Pageable pageable);

    /**
     * 查询未软删除的文档（用于导出）
     *
     * @param tenantId 租户ID
     * @return 文档列表
     */
    @Query("SELECT d FROM Document d WHERE d.deletedAt IS NULL " +
           "AND (:tenantId IS NULL OR d.tenantId = :tenantId) " +
           "ORDER BY d.createdAt DESC")
    List<Document> findAllActive(@Param("tenantId") String tenantId);

    /**
     * 根据租户ID查询文档（分页）
     *
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 文档分页列表
     */
    Page<Document> findByTenantId(String tenantId, Pageable pageable);

    /**
     * 根据用户ID查询文档（分页）
     *
     * @param userId   用户ID
     * @param pageable 分页参数
     * @return 文档分页列表
     */
    Page<Document> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据文件存储键查询文档
     *
     * @param fileKey 文件存储键
     * @return 文档信息
     */
    Optional<Document> findByFileKey(String fileKey);

    /**
     * 统计指定租户下的文档总数
     *
     * @param tenantId 租户ID
     * @return 文档数量
     */
    long countByTenantId(String tenantId);
}
