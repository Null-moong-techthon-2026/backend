package kr.boothrock.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class OrganizationMembershipId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    protected OrganizationMembershipId() {}

    public OrganizationMembershipId(UUID organizationId, UUID accountId) {
        this.organizationId = organizationId;
        this.accountId = accountId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrganizationMembershipId that)) return false;
        return Objects.equals(organizationId, that.organizationId)
                && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationId, accountId);
    }
}
