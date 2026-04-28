package com.ai.interview.repository;

import com.ai.interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层。
 * <p>所有查询均显式携带 {@code deletedAt IS NULL} 条件，以排除软删除用户。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按用户名查询未删除的用户（用于登录校验）。 */
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    /** 按邮箱查询未删除的用户（用于找回密码等场景）。 */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /** 检查用户名是否已存在（注册唯一性校验，不区分是否已删除）。 */
    boolean existsByUsername(String username);

    /** 检查邮箱是否已存在（注册唯一性校验，不区分是否已删除）。 */
    boolean existsByEmail(String email);
}
