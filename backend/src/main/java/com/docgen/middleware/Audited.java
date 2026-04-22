package com.docgen.middleware;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志自定义注解
 * 用于标记需要记录审计日志的接口方法
 * 配合 AuditLogInterceptor 使用
 *
 * 使用示例：
 * <pre>
 *     &#64;Audited(action = "创建文档", resource = "document")
 *     &#64;PostMapping("/documents")
 *     public Result&lt;Document&gt; createDocument(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /**
     * 审计动作描述，例如："创建文档"、"删除模板"
     */
    String action();

    /**
     * 审计资源类型，例如："document"、"template"、"fragment"
     */
    String resource();
}
