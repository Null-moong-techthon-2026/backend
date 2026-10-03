package kr.boothrock.api.auth.repository;

import java.util.UUID;
import kr.boothrock.api.auth.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {}
