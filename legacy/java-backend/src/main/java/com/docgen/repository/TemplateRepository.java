package com.docgen.repository;

import com.docgen.entity.Template;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 模板数据访问层
 */
@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    /**
     * 根据名称模糊查询（忽略大小写）
     */
    Page<Template> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    /**
     * 根据分类查询
     */
    Page<Template> findByCategory(String category, Pageable pageable);

    /**
     * 根据名称模糊查询和分类联合查询
     */
    Page<Template> findByNameContainingIgnoreCaseAndCategory(String keyword, String category, Pageable pageable);

    /**
     * 查询所有不重复的分类
     */
    List<String> findDistinctCategory();

}
