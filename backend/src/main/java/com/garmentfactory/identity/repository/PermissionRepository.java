package com.garmentfactory.identity.repository;

import com.garmentfactory.identity.entity.Permission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByCodeAsc();

    List<Permission> findByCodeIn(Collection<String> codes);
}