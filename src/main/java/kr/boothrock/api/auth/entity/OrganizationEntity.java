package kr.boothrock.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations", schema = "app")
public class OrganizationEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "organization_status", nullable = false, length = 20)
    private String organizationStatus;
    @Column(name = "contact_email", length = 254)
    private String contactEmail;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationEntity() {}

    public OrganizationEntity(String name, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.organizationStatus = "ACTIVE";
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
}
