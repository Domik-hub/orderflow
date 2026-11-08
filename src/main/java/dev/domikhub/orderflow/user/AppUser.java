package dev.domikhub.orderflow.user;

import jakarta.persistence.*;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    protected AppUser() {}
    public AppUser(String email, String displayName, String passwordHash, Role role) {
        this.email = email; this.displayName = displayName; this.passwordHash = passwordHash; this.role = role;
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public enum Role { USER, ADMIN }
}
