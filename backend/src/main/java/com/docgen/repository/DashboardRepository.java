package com.docgen.repository;

import com.docgen.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardRepository extends JpaRepository<User, Long> {

    @Query("SELECT COUNT(t) FROM Template t WHERE t.deletedAt IS NULL AND t.tenantId = :tenantId")
    long countTemplates(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(t) FROM Template t WHERE t.status = 'published' AND t.deletedAt IS NULL AND t.tenantId = :tenantId")
    long countPublishedTemplates(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(t) FROM Template t WHERE t.status = 'pending' AND t.deletedAt IS NULL AND t.tenantId = :tenantId")
    long countPendingTemplates(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.deletedAt IS NULL AND d.tenantId = :tenantId")
    long countDocuments(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.tenantId = :tenantId")
    long countUsers(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(f) FROM Fragment f WHERE f.deletedAt IS NULL AND f.tenantId = :tenantId")
    long countFragments(@Param("tenantId") String tenantId);
}
