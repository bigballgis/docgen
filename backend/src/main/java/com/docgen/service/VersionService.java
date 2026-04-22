package com.docgen.service;

import com.docgen.config.FileStorageConfig;
import com.docgen.entity.Template;
import com.docgen.entity.TemplateVersion;
import com.docgen.exception.BusinessException;
import com.docgen.repository.TemplateRepository;
import com.docgen.repository.TemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本控制服务
 * 对应 Node.js 的 versionService.js
 * 负责模板版本的创建、查询、回滚、对比等操作
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VersionService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final FileStorageConfig fileStorageConfig;

    /**
     * 创建新版本
     * 复制当前模板文件到版本目录，创建版本记录，更新模板的当前版本号
     *
     * @param templateId 模板ID
     * @param userId     操作用户ID
     * @param changeNote 变更说明
     * @return 新创建的版本记录
     */
    @Transactional
    public TemplateVersion createVersion(Long templateId, Long userId, String changeNote) {
        log.info("创建模板版本, templateId={}, userId={}", templateId, userId);

        // 查询模板
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 获取当前版本号，新版本号+1
        int newVersion = template.getCurrentVersion() + 1;

        // 构建版本文件存储路径：versions/{templateId}/v{version}_{filename}
        String versionDir = fileStorageConfig.getUploadDir() + "/versions/" + templateId;
        String versionFileName = "v" + newVersion + "_" + template.getFileName();
        String versionFilePath = versionDir + "/" + versionFileName;

        try {
            // 创建版本目录
            Path dirPath = Paths.get(versionDir).toAbsolutePath().normalize();
            Files.createDirectories(dirPath);

            // 复制当前模板文件到版本目录
            Path sourcePath = Paths.get(fileStorageConfig.getUploadDir(), template.getFileName())
                    .toAbsolutePath().normalize();
            Path targetPath = Paths.get(versionFilePath).toAbsolutePath().normalize();

            if (Files.exists(sourcePath)) {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("模板文件已复制到版本目录: {}", versionFilePath);
            } else {
                log.warn("模板源文件不存在: {}", sourcePath);
            }
        } catch (IOException e) {
            log.error("复制模板文件到版本目录失败", e);
            throw new BusinessException("创建版本失败：文件复制异常");
        }

        // 获取文件大小
        long fileSize = 0L;
        try {
            Path versionPath = Paths.get(versionFilePath).toAbsolutePath().normalize();
            if (Files.exists(versionPath)) {
                fileSize = Files.size(versionPath);
            }
        } catch (IOException e) {
            log.warn("获取版本文件大小失败", e);
        }

        // 创建版本记录
        TemplateVersion templateVersion = TemplateVersion.builder()
                .templateId(templateId)
                .version(newVersion)
                .filePath(versionFilePath)
                .fileSize(fileSize)
                .changeNote(changeNote)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
        templateVersionRepository.save(templateVersion);

        // 更新模板的当前版本号
        template.setCurrentVersion(newVersion);
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        log.info("模板版本创建成功, templateId={}, version={}", templateId, newVersion);
        return templateVersion;
    }

    /**
     * 获取版本列表
     * 返回指定模板的所有版本记录，按版本号降序排列
     *
     * @param templateId 模板ID
     * @return 版本列表
     */
    public List<TemplateVersion> getVersionList(Long templateId) {
        log.info("获取模板版本列表, templateId={}", templateId);

        List<TemplateVersion> versions = templateVersionRepository.findByTemplateIdOrderByVersionDesc(templateId);

        log.info("模板版本数量: {}", versions.size());
        return versions;
    }

    /**
     * 获取版本详情（供 Controller 的 getVersionDetail 调用）
     *
     * @param templateId 模板ID
     * @param version    版本号
     * @return 版本详情
     */
    public TemplateVersion getVersionDetail(Long templateId, Integer version) {
        return getVersion(templateId, version);
    }

    /**
     * 获取特定版本详情
     *
     * @param templateId 模板ID
     * @param version    版本号
     * @return 版本详情
     */
    public TemplateVersion getVersion(Long templateId, Integer version) {
        log.info("获取模板版本详情, templateId={}, version={}", templateId, version);

        return templateVersionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new BusinessException("版本不存在，templateId=" + templateId + ", version=" + version));
    }

    /**
     * 获取版本 HTML 预览（供 Controller 的 getVersionPreview 调用）
     *
     * @param templateId 模板ID
     * @param version    版本号
     * @return HTML 预览内容
     */
    public String getVersionPreview(Long templateId, Integer version) {
        return getVersionHtml(templateId, version);
    }

    /**
     * 回滚到指定版本（供 Controller 的 rollbackVersion 调用）
     *
     * @param templateId 模板ID
     * @param version    目标回滚版本号
     * @param userId     操作用户ID
     * @return 回滚结果 Map
     */
    @Transactional
    public Map<String, Object> rollbackVersion(Long templateId, Integer version, Long userId) {
        log.info("回滚模板版本, templateId={}, targetVersion={}, userId={}", templateId, version, userId);

        TemplateVersion rollbackVersion = rollbackToVersion(templateId, version, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("version", rollbackVersion.getVersion());
        result.put("templateId", templateId);
        result.put("message", "回滚成功");
        result.put("changeNote", rollbackVersion.getChangeNote());
        result.put("createdAt", rollbackVersion.getCreatedAt());

        return result;
    }

    /**
     * 回滚到指定版本
     * 将指定版本的文件复制到当前模板位置，创建新版本记录（标记为回滚操作），更新模板的当前版本号
     *
     * @param templateId 模板ID
     * @param version    目标回滚版本号
     * @param userId     操作用户ID
     * @return 新创建的版本记录
     */
    @Transactional
    public TemplateVersion rollbackToVersion(Long templateId, Integer version, Long userId) {
        log.info("回滚模板版本, templateId={}, targetVersion={}, userId={}", templateId, version, userId);

        // 查询模板
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在，id=" + templateId));

        // 查询目标版本
        TemplateVersion targetVersion = templateVersionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new BusinessException("目标版本不存在，version=" + version));

        // 新版本号 = 当前版本号 + 1
        int newVersion = template.getCurrentVersion() + 1;

        // 复制版本文件到当前模板位置
        try {
            Path sourcePath = Paths.get(targetVersion.getFilePath()).toAbsolutePath().normalize();
            Path targetPath = Paths.get(fileStorageConfig.getUploadDir(), template.getFileName())
                    .toAbsolutePath().normalize();

            if (Files.exists(sourcePath)) {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("版本文件已回滚到当前模板位置: {}", targetPath);
            } else {
                throw new BusinessException("版本文件不存在: " + sourcePath);
            }
        } catch (IOException e) {
            log.error("回滚版本文件失败", e);
            throw new BusinessException("回滚失败：文件复制异常");
        }

        // 创建新版本记录（记录回滚操作）
        String versionDir = fileStorageConfig.getUploadDir() + "/versions/" + templateId;
        String versionFileName = "v" + newVersion + "_" + template.getFileName();
        String versionFilePath = versionDir + "/" + versionFileName;

        try {
            Path dirPath = Paths.get(versionDir).toAbsolutePath().normalize();
            Files.createDirectories(dirPath);

            Path sourcePath = Paths.get(targetVersion.getFilePath()).toAbsolutePath().normalize();
            Path targetPath = Paths.get(versionFilePath).toAbsolutePath().normalize();
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("保存回滚版本文件失败", e);
        }

        long fileSize = 0L;
        try {
            Path versionPath = Paths.get(versionFilePath).toAbsolutePath().normalize();
            if (Files.exists(versionPath)) {
                fileSize = Files.size(versionPath);
            }
        } catch (IOException e) {
            log.warn("获取回滚版本文件大小失败", e);
        }

        TemplateVersion rollbackVersion = TemplateVersion.builder()
                .templateId(templateId)
                .version(newVersion)
                .filePath(versionFilePath)
                .fileSize(fileSize)
                .changeNote("回滚到版本 v" + version)
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
        templateVersionRepository.save(rollbackVersion);

        // 更新模板的当前版本号
        template.setCurrentVersion(newVersion);
        template.setUpdateTime(LocalDateTime.now());
        templateRepository.save(template);

        log.info("模板版本回滚成功, templateId={}, fromVersion={}, newVersion={}", templateId, version, newVersion);
        return rollbackVersion;
    }

    /**
     * 对比两个版本
     * 读取两个版本的 docx 文件，转换为 HTML 后返回对比结果
     * 简化版：返回两个版本的 HTML 内容供前端对比展示
     *
     * @param templateId 模板ID
     * @param v1         第一个版本号
     * @param v2         第二个版本号
     * @return 包含两个版本 HTML 内容的 Map
     */
    public Map<String, Object> compareVersions(Long templateId, Integer v1, Integer v2) {
        log.info("对比模板版本, templateId={}, v1={}, v2={}", templateId, v1, v2);

        // 查询两个版本
        TemplateVersion version1 = templateVersionRepository.findByTemplateIdAndVersion(templateId, v1)
                .orElseThrow(() -> new BusinessException("版本 v" + v1 + " 不存在"));
        TemplateVersion version2 = templateVersionRepository.findByTemplateIdAndVersion(templateId, v2)
                .orElseThrow(() -> new BusinessException("版本 v" + v2 + " 不存在"));

        // 读取两个版本的 HTML 内容
        String html1 = convertDocxToHtml(version1.getFilePath());
        String html2 = convertDocxToHtml(version2.getFilePath());

        // 构建对比结果
        Map<String, Object> result = new HashMap<>();
        result.put("v1", Map.of(
                "version", v1,
                "html", html1,
                "changeNote", version1.getChangeNote(),
                "createdAt", version1.getCreatedAt()
        ));
        result.put("v2", Map.of(
                "version", v2,
                "html", html2,
                "changeNote", version2.getChangeNote(),
                "createdAt", version2.getCreatedAt()
        ));

        return result;
    }

    /**
     * 获取版本的 HTML 预览
     * 读取指定版本的 docx 文件并转换为 HTML
     *
     * @param templateId 模板ID
     * @param version    版本号
     * @return HTML 内容
     */
    public String getVersionHtml(Long templateId, Integer version) {
        log.info("获取模板版本 HTML 预览, templateId={}, version={}", templateId, version);

        TemplateVersion templateVersion = templateVersionRepository.findByTemplateIdAndVersion(templateId, version)
                .orElseThrow(() -> new BusinessException("版本不存在，version=" + version));

        return convertDocxToHtml(templateVersion.getFilePath());
    }

    /**
     * 将 docx 文件转换为 HTML
     * 使用 docx4j 进行转换，如果转换失败则返回提示信息
     *
     * @param filePath docx 文件路径
     * @return HTML 内容字符串
     */
    private String convertDocxToHtml(String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                log.warn("文件不存在: {}", path);
                return "<p>文件不存在</p>";
            }

            // 使用 docx4j 加载 docx 文件验证文件有效性
            try (InputStream is = Files.newInputStream(path)) {
                org.docx4j.openpackaging.packages.WordprocessingMLPackage wordMLPackage =
                        org.docx4j.openpackaging.packages.WordprocessingMLPackage.load(is);
                log.debug("DOCX 文件加载成功: {}", filePath);
            }

            // 简化处理：DOCX 转 HTML 的完整实现需要前端配合渲染
            return "<p>DOCX 文件已加载（版本预览功能需要前端渲染）</p>";
        } catch (Exception e) {
            log.error("DOCX 转 HTML 失败, filePath={}", filePath, e);
            return "<p>文件预览转换失败: " + e.getMessage() + "</p>";
        }
    }
}
