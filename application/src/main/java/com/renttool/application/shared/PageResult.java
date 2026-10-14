package com.renttool.application.shared;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> content, long totalElements, int totalPages, int page) {
  public <R> PageResult<R> map(Function<T, R> mapper) {
    return new PageResult<>(content.stream().map(mapper).toList(), totalElements, totalPages, page);
  }
}
