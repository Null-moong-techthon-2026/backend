package kr.boothrock.api.auth.repository;

import java.util.Optional;
import java.util.UUID;
import kr.boothrock.api.auth.entity.LocalCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredentialEntity, UUID> {
    boolean existsByLoginId(String loginId);
    Optional<LocalCredentialEntity> findByLoginId(String loginId);
}
