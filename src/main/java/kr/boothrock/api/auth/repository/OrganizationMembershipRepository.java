package kr.boothrock.api.auth.repository;

import kr.boothrock.api.auth.entity.OrganizationMembershipEntity;
import kr.boothrock.api.auth.entity.OrganizationMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository
        extends JpaRepository<OrganizationMembershipEntity, OrganizationMembershipId> {}
