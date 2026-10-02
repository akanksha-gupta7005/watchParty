package com.watchparty.repository;

import com.watchparty.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsernameKey(String usernameKey);

    boolean existsByUsernameKey(String usernameKey);
}
