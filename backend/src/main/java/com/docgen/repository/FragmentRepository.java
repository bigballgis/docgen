package com.docgen.repository;

import com.docgen.entity.Fragment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 片段数据访问层
 */
public interface FragmentRepository extends JpaRepository<Fragment, Long> {

    /**
     * 带动态条件查询片段（数据库层面过滤，避免全表扫描+内存过滤）
     *
     * @param tenantId 租户ID
     * @param keyword  搜索关键字（匹配名称和描述）
     * @param category 分类过滤
     * @param status   状态过滤
     * @param pageable 分页参数
     * @return 片段分页列表
     */
    @Query("SELECT f FROM Fragment f WHERE f.deletedAt IS NULL " +
           "AND (:tenantId IS NULL OR f.tenantId = :tenantId) " +
           "AND (:keyword IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR f.category = :category) " +
           "AND (:status IS NULL OR f.status = :status) " +
           "ORDER BY f.createdAt DESC")
    Page<Fragment> findWithFilters(@Param("tenantId") String tenantId,
                                   @Param("keyword") String keyword,
                                   @Param("category") String category,
                                   @Param("status") String status,
                                   Pageable pageable);

    /**
     * 根据租户ID查询未删除的片段（分页）
     *
     * @param tenantId 租户ID
     * @param pageable 分页参数
     * @return 片段分页列表
     */
    Page<Fragment> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);

    /**
     * 查询所有未删除的片段（分页）
     *
     * @param pageable 分页参数
     * @return 片段分页列表
     */
    Page<Fragment> findByDeletedAtIsNull(Pageable pageable);

    /**
     * 根据分类查询未删除的片段列表
     *
     * @param category 片段分类
     * @return 片段列表
     */
    List<Fragment> findByCategoryAndDeletedAtIsNull(String category);

    /**
     * 根据租户ID查询有分类且未删除的片段列表
     *
     * @param tenantId 租户ID
     * @return 有分类的片段列表
     */
    List<Fragment> findByTenantIdAndDeletedAtIsNullAndCategoryIsNotNull(String tenantId);

    /**
     * 统计指定租户下的片段总数
     *
     * @param tenantId 租户ID
     * @return 片段数量
     */
    long countByTenantId(String tenantId);

    /**
     * 查询指定租户下所有不重复的分类（数据库层面去重）
     *
     * @param tenantId 租户ID
     * @return 分类列表
     */
    @Query("SELECT DISTINCT f.category FROM Fragment f WHERE f.deletedAt IS NULL AND f.tenantId = :tenantId")
    List<String> findDistinctCategories(@Param("tenantId") String tenantId);
}
