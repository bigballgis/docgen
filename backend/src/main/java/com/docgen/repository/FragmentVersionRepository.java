package com.docgen.repository;

import com.docgen.entity.FragmentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 片段版本数据访问层
 */
public interface FragmentVersionRepository extends JpaRepository<FragmentVersion, Long> {

    /**
     * 根据片段ID查询所有版本（按版本号降序排列）
     *
     * @param fragmentId 片段ID
     * @return 版本列表
     */
    List<FragmentVersion> findByFragmentIdOrderByVersionDesc(Long fragmentId);

    /**
     * 根据片段ID和版本号查询指定版本
     *
     * @param fragmentId 片段ID
     * @param version    版本号
     * @return 片段版本信息
     */
    Optional<FragmentVersion> findByFragmentIdAndVersion(Long fragmentId, Integer version);
}
