package com.renttool.infrastructure.identity.persistence;

import jakarta.persistence.*;

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
}
