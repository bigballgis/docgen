package com.docgen.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板实体类
 */
@Data
@Entity
@Table(name = "templates")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 模板名称
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 描述
     */
    @Column(length = 500)
    private String description;

    /**
     * 存储文件名（服务器端唯一文件名）
     */
    @Column(nullable = false, length = 255)
    private String fileName;

    /**
     * 原始文件名（用户上传时的文件名）
     */
    @Column(nullable = false, length = 255)
    private String originalFileName;

    /**
     * 模板字段定义（JSON 字符串）
     */
    @Column(columnDefinition = "TEXT")
    private String fields;

    /**
     * 分类
     */
    @Column(length = 50)
    private String category;

    /**
     * 创建时间
     */
    @Column(updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }

}
