package com.garmentfactory.identity.repository;

import com.garmentfactory.identity.entity.Role;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    List<Role> findByCodeIn(Collection<String> codes);

    @Query("select distinct r from Role r left join fetch r.permissions order by r.code")
    List<Role> findAllWithPermissions();
}