package com.renttool.identity.domain;

public record Account(
    Long id, String name, String email, String passwordHash, Role role, boolean active) {
  public enum Role {
    CLIENTE,
    PROVEEDOR,
    ADMINISTRADOR
  }
}
