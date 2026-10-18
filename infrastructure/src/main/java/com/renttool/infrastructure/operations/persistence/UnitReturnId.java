package com.renttool.infrastructure.operations.persistence;
import java.io.Serializable;
import java.util.Objects;

public class UnitReturnId implements Serializable {
  public Long reservationId;
  public Long unitId;
  public UnitReturnId() {}
  public UnitReturnId(Long reservationId, Long unitId) { this.reservationId = reservationId; this.unitId = unitId; }
  @Override public boolean equals(Object other) {
    return other instanceof UnitReturnId key
        && Objects.equals(reservationId, key.reservationId) && Objects.equals(unitId, key.unitId);
  }
  @Override public int hashCode() { return Objects.hash(reservationId, unitId); }
}
