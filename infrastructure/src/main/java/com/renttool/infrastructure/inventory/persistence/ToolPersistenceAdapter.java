package com.renttool.infrastructure.inventory.persistence;

import com.renttool.application.inventory.port.out.ToolRepository;
import com.renttool.domain.inventory.Tool;
import com.renttool.application.shared.PageQuery;
import com.renttool.application.shared.PageResult;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class ToolPersistenceAdapter implements ToolRepository {
  private final ToolJpaRepository jpa;

  ToolPersistenceAdapter(ToolJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Optional<Tool> findById(Long id) {
    return jpa.findById(id).map(ToolPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<Tool> findByIdForUpdate(Long id) {
    return jpa.findByIdForUpdate(id).map(ToolPersistenceAdapter::toDomain);
  }

  @Override
  public List<Long> findIdsByProviderId(Long providerId) {
    return jpa.findIdsByProviderId(providerId);
  }

  @Override
  public Tool save(Tool tool) {
    return toDomain(jpa.save(toEntity(tool)));
  }

  @Override
  public PageResult<Tool> searchActive(SearchCriteria criteria, PageQuery page) {
    Specification<ToolEntity> spec =
        (root, query, cb) -> {
          var predicates = new ArrayList<Predicate>();
          predicates.add(cb.isTrue(root.get("active")));
          if (criteria.text() != null)
            predicates.add(
                cb.greaterThan(
                    cb.locate(
                        cb.lower(root.get("name")), criteria.text().toLowerCase(Locale.ROOT)),
                    0));
          if (criteria.categoryId() != null)
            predicates.add(cb.equal(root.get("categoryId"), criteria.categoryId()));
          if (criteria.providerId() != null)
            predicates.add(cb.equal(root.get("providerId"), criteria.providerId()));
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    var result =
        jpa.findAll(spec, PageRequest.of(page.page(), page.size(), Sort.by("id").descending()));
    return new PageResult<>(
        result.getContent().stream().map(ToolPersistenceAdapter::toDomain).toList(),
        result.getTotalElements(),
        result.getTotalPages(),
        page.page());
  }

  @Override
  public boolean isEmpty() {
    return jpa.count() == 0;
  }

  private static Tool toDomain(ToolEntity e) {
    return new Tool(e.id, e.providerId, e.categoryId, e.name, e.description, e.dailyRate, e.active);
  }

  private static ToolEntity toEntity(Tool t) {
    var e = new ToolEntity();
    e.id = t.id();
    e.providerId = t.providerId();
    e.categoryId = t.categoryId();
    e.name = t.name();
    e.description = t.description();
    e.dailyRate = t.dailyRate();
    e.active = t.active();
    return e;
  }
}
