package com.renttool.infrastructure.identity.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class AccountEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String name;

  @Column(unique = true)
  public String email;

  @Column(name = "password_hash")
  public String passwordHash;

  public String role;
  public boolean active;

  @Column(name = "must_change_password")
  public boolean mustChangePassword;

  /** UTC, como el resto de instantes; null si la cuenta no fue eliminada. */
  @Column(name = "removed_at")
  public LocalDateTime removedAt;

  @Column(length = 20)
  public String phone;
}
