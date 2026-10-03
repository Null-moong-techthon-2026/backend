package kr.boothrock.api.auth.repository;

import java.util.UUID;
import kr.boothrock.api.auth.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {}
