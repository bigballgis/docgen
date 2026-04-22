package com.docgen.service;

import com.docgen.dto.FragmentRequest;
import com.docgen.dto.FragmentUpdateRequest;
import com.docgen.entity.Fragment;
import com.docgen.entity.FragmentVersion;
import com.docgen.exception.BusinessException;
import com.docgen.repository.FragmentRepository;
import com.docgen.repository.FragmentVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 片段服务
 * 对应 Node.js 的 fragmentService.js
 * 负责文档片段的增删改查、版本管理、字段解析等功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FragmentService {

    private final FragmentRepository fragmentRepository;
    private final FragmentVersionRepository fragmentVersionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 HTML 内容中提取占位符字段
     * 支持 {xxx} 和 ${xxx} 两种格式，并猜测字段类型
     *
     * @param contentHtml HTML 内容
     * @return 字段列表（JSON 字符串），每个字段包含 name 和 type
     */
    public String parseFragmentFields(String contentHtml) {
        log.info("解析片段字段, 内容长度={}", contentHtml != null ? contentHtml.length() : 0);

        if (contentHtml == null || contentHtml.isEmpty()) {
            return "[]";
        }

        // 匹配 {xxx} 和 ${xxx} 格式的占位符
        Pattern pattern = Pattern.compile("\\$?\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(contentHtml);

        // 使用 LinkedHashSet 保持插入顺序并去重
        Set<String> fieldNames = new LinkedHashSet<>();
        while (matcher.find()) {
            String fieldName = matcher.group(1).trim();
            if (!fieldName.isEmpty()) {
                fieldNames.add(fieldName);
            }
        }

        // 根据字段名猜测类型
        List<Map<String, String>> fields = new ArrayList<>();
        for (String fieldName : fieldNames) {
            Map<String, String> field = new HashMap<>();
            field.put("name", fieldName);
            field.put("type", guessFieldType(fieldName));
            fields.add(field);
        }

        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            log.error("字段列表序列化失败", e);
            return "[]";
        }
    }

    /**
     * 根据字段名猜测字段类型
     *
     * @param fieldName 字段名
     * @return 猜测的字段类型
     */
    private String guessFieldType(String fieldName) {
        String lowerName = fieldName.toLowerCase();

        // 日期相关字段
        if (lowerName.contains("date") || lowerName.contains("日期") || lowerName.contains("时间")) {
            return "date";
        }
        // 金额相关字段
        if (lowerName.contains("amount") || lowerName.contains("金额") || lowerName.contains("price") || lowerName.contains("价格")) {
            return "number";
        }
        // 数量相关字段
        if (lowerName.contains("count") || lowerName.contains("数量") || lowerName.contains("num") || lowerName.contains("total")) {
            return "number";
        }
        // 电话相关字段
        if (lowerName.contains("phone") || lowerName.contains("tel") || lowerName.contains("电话") || lowerName.contains("手机")) {
            return "text";
        }
        // 邮箱相关字段
        if (lowerName.contains("email") || lowerName.contains("邮箱") || lowerName.contains("邮件")) {
            return "text";
        }
        // 图片相关字段
        if (lowerName.contains("image") || lowerName.contains("图片") || lowerName.contains("photo") || lowerName.contains("照片")) {
            return "image";
        }
        // 默认为文本类型
        return "text";
    }

    /**
     * 创建片段
     * 自动解析 HTML 中的占位符字段，创建片段记录和初始版本记录
     *
     * @param request  创建请求
     * @param userId   创建人用户ID
     * @param tenantId 租户ID
     * @return 创建的片段信息
     */
    @Transactional
    public Fragment createFragment(FragmentRequest request, Long userId, String tenantId) {
        log.info("创建片段, name={}, userId={}, tenantId={}", request.getName(), userId, tenantId);

        // 自动解析字段
        String fields = parseFragmentFields(request.getContent());

        // 序列化标签为 JSON 数组
        String tagsJson = "[]";
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            try {
                tagsJson = objectMapper.writeValueAsString(request.getTags());
            } catch (JsonProcessingException e) {
                log.warn("标签序列化失败", e);
            }
        }

        // 创建 Fragment 记录
        Fragment fragment = Fragment.builder()
                .name(request.getName())
                .description(request.getDescription() != null ? request.getDescription() : "")
                .category(request.getCategory() != null ? request.getCategory() : "")
                .contentHtml(request.getContent())
                .contentDocxPath("")
                .fields(fields)
                .tags(tagsJson)
                .tenantId(tenantId != null ? tenantId : "default")
                .status("draft")
                .currentVersion(1)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        fragment = fragmentRepository.save(fragment);

        // 创建初始 FragmentVersion 记录（version=1）
        FragmentVersion fragmentVersion = FragmentVersion.builder()
                .fragmentId(fragment.getId())
                .version(1)
                .contentHtml(request.getContent())
                .contentDocxPath("")
                .fields(fields)
                .changeNote("初始版本")
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
        fragmentVersionRepository.save(fragmentVersion);

        log.info("片段创建成功, id={}, name={}", fragment.getId(), fragment.getName());
        return fragment;
    }

    /**
     * 更新片段
     * 如果内容发生变化，自动创建新版本
     *
     * @param id      片段ID
     * @param request 更新请求
     * @param userId  操作用户ID
     * @return 更新后的片段信息
     */
    @Transactional
    public Fragment updateFragment(Long id, FragmentUpdateRequest request, Long userId) {
        log.info("更新片段, id={}, userId={}", id, userId);

        // 查询片段
        Fragment fragment = fragmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("片段不存在，id=" + id));

        // 检查是否已被软删除
        if (fragment.getDeletedAt() != null) {
            throw new BusinessException("该片段已被删除");
        }

        // 检查内容是否发生变化
        boolean contentChanged = request.getContent() != null
                && !request.getContent().equals(fragment.getContentHtml());

        // 更新基本信息
        if (request.getName() != null) {
            fragment.setName(request.getName());
        }
        if (request.getDescription() != null) {
            fragment.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            fragment.setCategory(request.getCategory());
        }
        if (request.getTags() != null) {
            try {
                fragment.setTags(objectMapper.writeValueAsString(request.getTags()));
            } catch (JsonProcessingException e) {
                log.warn("标签序列化失败", e);
            }
        }
        if (request.getContent() != null) {
            fragment.setContentHtml(request.getContent());
        }

        // 如果内容发生变化，自动创建新版本
        if (contentChanged) {
            int newVersion = fragment.getCurrentVersion() + 1;
            String fields = parseFragmentFields(request.getContent());

            // 创建新版本记录
            FragmentVersion fragmentVersion = FragmentVersion.builder()
                    .fragmentId(fragment.getId())
                    .version(newVersion)
                    .contentHtml(request.getContent())
                    .contentDocxPath(fragment.getContentDocxPath())
                    .fields(fields)
                    .changeNote(request.getChangeNote() != null ? request.getChangeNote() : "更新内容")
                    .createdBy(userId)
                    .createdAt(LocalDateTime.now())
                    .build();
            fragmentVersionRepository.save(fragmentVersion);

            // 更新片段的当前版本号和字段
            fragment.setCurrentVersion(newVersion);
            fragment.setFields(fields);

            log.info("片段内容已变更，自动创建新版本, fragmentId={}, newVersion={}", fragment.getId(), newVersion);
        }

        fragment.setUpdatedAt(LocalDateTime.now());
        fragment = fragmentRepository.save(fragment);

        log.info("片段更新成功, id={}", fragment.getId());
        return fragment;
    }

    /**
     * 获取片段详情
     *
     * @param id 片段ID
     * @return 片段信息
     */
    public Fragment getFragmentById(Long id) {
        log.info("获取片段详情, id={}", id);

        Fragment fragment = fragmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("片段不存在，id=" + id));

        if (fragment.getDeletedAt() != null) {
            throw new BusinessException("该片段已被删除");
        }

        return fragment;
    }

    /**
     * 获取片段列表（分页查询，支持多条件过滤）
     * 排除已软删除的片段
     *
     * @param tenantId 租户ID
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @param keyword  搜索关键词（模糊匹配名称和描述）
     * @param category 分类过滤
     * @param tags     标签过滤（逗号分隔的字符串）
     * @param status   状态过滤
     * @return 分页片段列表
     */
    public Page<Fragment> getFragmentList(String tenantId, int page, int size,
                                          String keyword, String category, String tags, String status) {
        log.info("获取片段列表, keyword={}, category={}, status={}, page={}, size={}, tenantId={}",
                keyword, category, status, page, size, tenantId);

        // 构建分页参数（按创建时间降序，与 Repository 查询中的 ORDER BY 一致）
        Pageable pageable = PageRequest.of(page, size);

        // 处理空字符串参数，转换为 null 以匹配 @Query 中的 IS NULL 条件
        String tid = (tenantId != null && !tenantId.isEmpty()) ? tenantId : null;
        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String cat = (category != null && !category.trim().isEmpty()) ? category.trim() : null;
        String st = (status != null && !status.trim().isEmpty()) ? status.trim() : null;

        // 使用数据库层面过滤的查询方法
        Page<Fragment> result = fragmentRepository.findWithFilters(tid, kw, cat, st, pageable);

        // 标签过滤仍需在内存中进行（因为标签存储为 JSON 字符串）
        if (tags != null && !tags.trim().isEmpty()) {
            List<String> tagList = Arrays.asList(tags.split(","));
            List<Fragment> filtered = result.getContent().stream()
                    .filter(f -> {
                        try {
                            List<String> fragmentTags = objectMapper.readValue(
                                    f.getTags() != null ? f.getTags() : "[]",
                                    new TypeReference<List<String>>() {});
                            return tagList.stream().anyMatch(fragmentTags::contains);
                        } catch (JsonProcessingException e) {
                            log.warn("解析片段标签失败, fragmentId={}", f.getId());
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
        }

        return result;
    }

    /**
     * 获取片段分类列表（供 Controller 的 getFragmentCategories 调用）
     *
     * @param tenantId 租户ID
     * @return 分类列表
     */
    public List<String> getFragmentCategories(String tenantId) {
        return getAllCategories(tenantId);
    }

    /**
     * 获取片段版本列表（供 Controller 的 getFragmentVersions 调用）
     *
     * @param fragmentId 片段ID
     * @return 版本列表（按版本号降序）
     */
    public List<FragmentVersion> getFragmentVersions(Long fragmentId) {
        return getFragmentVersionList(fragmentId);
    }

    /**
     * 软删除片段
     * 设置 deletedAt 时间戳，不实际删除数据库记录
     *
     * @param id 片段ID
     */
    @Transactional
    public void deleteFragment(Long id) {
        log.info("删除片段, id={}", id);

        Fragment fragment = fragmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("片段不存在，id=" + id));

        if (fragment.getDeletedAt() != null) {
            throw new BusinessException("该片段已被删除");
        }

        fragment.setDeletedAt(LocalDateTime.now());
        fragmentRepository.save(fragment);

        log.info("片段已软删除, id={}", id);
    }

    /**
     * 获取所有不重复的分类列表
     *
     * @param tenantId 租户ID
     * @return 分类列表
     */
    public List<String> getAllCategories(String tenantId) {
        log.info("获取所有片段分类, tenantId={}", tenantId);

        if (tenantId != null && !tenantId.isEmpty()) {
            return fragmentRepository.findDistinctCategories(tenantId).stream()
                    .filter(c -> c != null && !c.isEmpty())
                    .collect(Collectors.toList());
        } else {
            List<Fragment> fragments = fragmentRepository.findByCategoryAndDeletedAtIsNull(null);
            return fragments.stream()
                    .map(Fragment::getCategory)
                    .filter(c -> c != null && !c.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    /**
     * 获取片段版本列表
     *
     * @param fragmentId 片段ID
     * @return 版本列表（按版本号降序）
     */
    public List<FragmentVersion> getFragmentVersionList(Long fragmentId) {
        log.info("获取片段版本列表, fragmentId={}", fragmentId);

        return fragmentVersionRepository.findByFragmentIdOrderByVersionDesc(fragmentId);
    }

    /**
     * 获取片段特定版本
     *
     * @param fragmentId 片段ID
     * @param version    版本号
     * @return 版本详情
     */
    public FragmentVersion getFragmentVersion(Long fragmentId, Integer version) {
        log.info("获取片段版本详情, fragmentId={}, version={}", fragmentId, version);

        return fragmentVersionRepository.findByFragmentIdAndVersion(fragmentId, version)
                .orElseThrow(() -> new BusinessException("片段版本不存在，fragmentId=" + fragmentId + ", version=" + version));
    }

    /**
     * 回滚片段到指定版本
     * 复制版本内容到当前片段，创建新版本记录
     *
     * @param fragmentId 片段ID
     * @param version    目标回滚版本号
     * @param userId     操作用户ID
     * @return 更新后的片段信息
     */
    @Transactional
    public Fragment rollbackFragmentVersion(Long fragmentId, Integer version, Long userId) {
        log.info("回滚片段版本, fragmentId={}, targetVersion={}, userId={}", fragmentId, version, userId);

        // 查询片段
        Fragment fragment = fragmentRepository.findById(fragmentId)
                .orElseThrow(() -> new BusinessException("片段不存在，id=" + fragmentId));

        if (fragment.getDeletedAt() != null) {
            throw new BusinessException("该片段已被删除");
        }

        // 查询目标版本
        FragmentVersion targetVersion = fragmentVersionRepository.findByFragmentIdAndVersion(fragmentId, version)
                .orElseThrow(() -> new BusinessException("目标版本不存在，version=" + version));

        // 创建新版本记录
        int newVersion = fragment.getCurrentVersion() + 1;
        FragmentVersion rollbackVersion = FragmentVersion.builder()
                .fragmentId(fragmentId)
                .version(newVersion)
                .contentHtml(targetVersion.getContentHtml())
                .contentDocxPath(targetVersion.getContentDocxPath())
                .fields(targetVersion.getFields())
                .changeNote("回滚到版本 v" + version)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
        fragmentVersionRepository.save(rollbackVersion);

        // 更新片段内容
        fragment.setContentHtml(targetVersion.getContentHtml());
        fragment.setContentDocxPath(targetVersion.getContentDocxPath());
        fragment.setFields(targetVersion.getFields());
        fragment.setCurrentVersion(newVersion);
        fragment.setUpdatedAt(LocalDateTime.now());
        fragmentRepository.save(fragment);

        log.info("片段版本回滚成功, fragmentId={}, fromVersion={}, newVersion={}", fragmentId, version, newVersion);
        return fragment;
    }

    /**
     * 对比片段两个版本
     * 简化版：返回两个版本的 HTML 内容供前端对比展示
     *
     * @param fragmentId 片段ID
     * @param v1         第一个版本号
     * @param v2         第二个版本号
     * @return 包含两个版本内容的 Map
     */
    public Map<String, Object> compareFragmentVersions(Long fragmentId, Integer v1, Integer v2) {
        log.info("对比片段版本, fragmentId={}, v1={}, v2={}", fragmentId, v1, v2);

        FragmentVersion version1 = fragmentVersionRepository.findByFragmentIdAndVersion(fragmentId, v1)
                .orElseThrow(() -> new BusinessException("版本 v" + v1 + " 不存在"));
        FragmentVersion version2 = fragmentVersionRepository.findByFragmentIdAndVersion(fragmentId, v2)
                .orElseThrow(() -> new BusinessException("版本 v" + v2 + " 不存在"));

        Map<String, Object> result = new HashMap<>();
        result.put("v1", Map.of(
                "version", v1,
                "html", version1.getContentHtml(),
                "changeNote", version1.getChangeNote(),
                "createdAt", version1.getCreatedAt()
        ));
        result.put("v2", Map.of(
                "version", v2,
                "html", version2.getContentHtml(),
                "changeNote", version2.getChangeNote(),
                "createdAt", version2.getCreatedAt()
        ));

        return result;
    }

    /**
     * 预览片段
     * 返回片段的 HTML 内容用于预览
     *
     * @param id 片段ID
     * @return HTML 预览内容
     */
    public String previewFragment(Long id) {
        log.info("预览片段, id={}", id);

        Fragment fragment = fragmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("片段不存在，id=" + id));

        if (fragment.getDeletedAt() != null) {
            throw new BusinessException("该片段已被删除");
        }

        return sanitizeHtml(fragment.getContentHtml());
    }

    private String sanitizeHtml(String html) {
        if (html == null || html.isEmpty()) return html;
        // 移除 script 标签和事件属性
        return html.replaceAll("(?i)<script[^>]*>.*?</script>", "")
                   .replaceAll("(?i)\\son\\w+\\s*=\\s*\"[^\"]*\"", "")
                   .replaceAll("(?i)\\son\\w+\\s*=\\s*'[^']*'", "")
                   .replaceAll("(?i)<iframe[^>]*>.*?</iframe>", "")
                   .replaceAll("(?i)<object[^>]*>.*?</object>", "")
                   .replaceAll("(?i)<embed[^>]*>", "");
    }
}
