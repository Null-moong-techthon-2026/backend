package kr.boothrock.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "local_credentials", schema = "app")
public class LocalCredentialEntity {
    @Id
    @Column(name = "account_id")
    private UUID accountId;
    @Column(name = "login_id", nullable = false, length = 30, unique = true)
    private String loginId;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LocalCredentialEntity() {}

    public LocalCredentialEntity(UUID accountId, String loginId, String passwordHash) {
        this.accountId = accountId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() { return accountId; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
}
