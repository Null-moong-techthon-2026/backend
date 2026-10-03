package kr.boothrock.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_memberships", schema = "app")
public class OrganizationMembershipEntity {
    @EmbeddedId
    private OrganizationMembershipId id;
    @Column(nullable = false, length = 20)
    private String role;
    @Column(name = "membership_status", nullable = false, length = 20)
    private String membershipStatus;
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected OrganizationMembershipEntity() {}

    public OrganizationMembershipEntity(UUID organizationId, UUID accountId) {
        this.id = new OrganizationMembershipId(organizationId, accountId);
        this.role = "OWNER";
        this.membershipStatus = "ACTIVE";
        this.joinedAt = Instant.now();
    }
}
