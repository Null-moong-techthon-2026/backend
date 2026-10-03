package kr.boothrock.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", schema = "app")
public class AccountEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String nickname;
    @Column(name = "account_status", nullable = false, length = 20)
    private String accountStatus;
    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntity() {}

    public AccountEntity(String nickname, String phoneNumber, String email) {
        this.id = UUID.randomUUID();
        this.nickname = nickname;
        this.accountStatus = "ACTIVE";
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getNickname() { return nickname; }
    public String getAccountStatus() { return accountStatus; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
}
