package com.omar.bankapi.repository;

import com.omar.bankapi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository

public interface UserRepository extends JpaRepository<User,Long> {

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByUsernameAndIdNotAndDeletedFalse(String username, Long id);

    boolean existsByEmailAndIdNotAndDeletedFalse(String email, Long id);

    Optional<User> findByIdAndDeletedFalse(Long id);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameIncludingDeleted(@Param("username") String username);

    Page<User> findAllByDeletedFalse(Pageable pageable);
}
