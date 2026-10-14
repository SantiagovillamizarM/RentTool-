package com.renttool.application.shared;

import com.renttool.domain.shared.exception.InvalidInputException;

public record PageQuery(int page, int size) {
  public static final int MAX_SIZE = 100;

  public PageQuery {
    if (page < 0 || size < 1 || size > MAX_SIZE)
      throw new InvalidInputException("Paginación inválida");
  }
}
