package com.renttool.inventory.infrastructure;

import com.renttool.inventory.application.*;
import com.renttool.inventory.domain.Tool;
import com.renttool.shared.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
  private final InventoryService service;
  private final JpaTools tools;
  private final JpaCategories categories;
  private final JpaUnits units;
  private final JpaImages images;

  public InventoryController(
      ToolRepository r, JpaTools t, JpaCategories c, JpaUnits u, JpaImages i) {
    service = new InventoryService(r);
    tools = t;
    categories = c;
    units = u;
    images = i;
  }

  public record ToolInput(
      @NotNull Long categoryId,
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 3000) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal dailyRate) {}

  public record UnitInput(
      @NotBlank @Size(max = 80) String code,
      @Pattern(regexp = "OPERATIVA|MANTENIMIENTO|RETIRADA") @NotNull String status) {}

  public record ImageInput(
      @NotBlank @Size(max = 1000) @Pattern(regexp = "https://\\S+|/(assets|uploads)/\\S+")
          String location) {}

  private boolean admin(Jwt j) {
    return "ADMINISTRADOR".equals(j.getClaimAsString("role"));
  }

  private Long actor(Jwt j) {
    if (j == null || (!admin(j) && !"PROVEEDOR".equals(j.getClaimAsString("role"))))
      throw new BusinessException(403, "Se requiere rol proveedor");
    return Long.valueOf(j.getSubject());
  }

  private void owns(Long id, Jwt j) {
    service.owned(id, actor(j), admin(j));
  }

  private void category(Long id) {
    if (!categories.existsById(id)) throw new BusinessException(400, "Categoría inválida");
  }

  private Map<String, Object> view(ToolEntity t) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", t.id);
    m.put("name", t.name);
    m.put("description", t.description);
    m.put("categoryId", t.categoryId);
    m.put("category", categories.findById(t.categoryId).map(c -> c.name).orElse(""));
    m.put("providerId", t.providerId);
    m.put("dailyRate", t.dailyRate);
    m.put("active", t.active);
    m.put("images", images.findByToolId(t.id));
    m.put(
        "operationalUnits",
        units.findByToolId(t.id).stream().filter(u -> u.status.equals("OPERATIVA")).count());
    return m;
  }

  @GetMapping("/categories")
  public List<CategoryEntity> categories() {
    return categories.findAll();
  }

  @GetMapping("/tools")
  public Map<String, Object> list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size,
      @AuthenticationPrincipal Jwt jwt) {
    if (page < 0 || size < 1 || size > 100) throw new BusinessException(400, "Paginación inválida");
    Long owner = mine ? actor(jwt) : null;
    Specification<ToolEntity> spec =
        (r, c, b) -> {
          var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
          p.add(b.isTrue(r.get("active")));
          if (!q.isBlank())
            p.add(b.greaterThan(b.locate(b.lower(r.get("name")), q.toLowerCase(Locale.ROOT)), 0));
          if (categoryId != null) p.add(b.equal(r.get("categoryId"), categoryId));
          if (owner != null) p.add(b.equal(r.get("providerId"), owner));
          return b.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    var result = tools.findAll(spec, PageRequest.of(page, size, Sort.by("id").descending()));
    return Map.of(
        "content",
        result.getContent().stream().map(this::view).toList(),
        "totalElements",
        result.getTotalElements(),
        "totalPages",
        result.getTotalPages(),
        "page",
        page);
  }

  @GetMapping("/tools/{id}")
  public Map<String, Object> get(@PathVariable Long id) {
    var t =
        tools
            .findById(id)
            .filter(x -> x.active)
            .orElseThrow(() -> new BusinessException(404, "Herramienta no encontrada"));
    return view(t);
  }

  @PostMapping("/tools")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Tool create(@Valid @RequestBody ToolInput t, @AuthenticationPrincipal Jwt j) {
    Long id = actor(j);
    category(t.categoryId());
    return service.save(
        new Tool(null, id, t.categoryId(), t.name(), t.description(), t.dailyRate(), true),
        id,
        admin(j));
  }

  @PutMapping("/tools/{id}")
  public Tool update(
      @PathVariable Long id, @Valid @RequestBody ToolInput t, @AuthenticationPrincipal Jwt j) {
    Long a = actor(j);
    category(t.categoryId());
    return service.save(
        new Tool(id, a, t.categoryId(), t.name(), t.description(), t.dailyRate(), true),
        a,
        admin(j));
  }

  @DeleteMapping("/tools/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt j) {
    service.retire(id, actor(j), admin(j));
  }

  @GetMapping("/tools/{id}/units")
  public List<UnitEntity> units(@PathVariable Long id, @AuthenticationPrincipal Jwt j) {
    owns(id, j);
    return units.findByToolId(id);
  }

  @PostMapping("/tools/{id}/units")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public UnitEntity unit(
      @PathVariable Long id, @Valid @RequestBody UnitInput input, @AuthenticationPrincipal Jwt j) {
    owns(id, j);
    var u = new UnitEntity();
    u.toolId = id;
    u.code = input.code();
    u.status = input.status();
    return units.save(u);
  }

  @PutMapping("/tools/{id}/units/{unitId}")
  public UnitEntity updateUnit(
      @PathVariable Long id,
      @PathVariable Long unitId,
      @Valid @RequestBody UnitInput input,
      @AuthenticationPrincipal Jwt j) {
    owns(id, j);
    var u =
        units
            .findById(unitId)
            .filter(x -> x.toolId.equals(id))
            .orElseThrow(() -> new BusinessException(404, "Unidad no encontrada"));
    u.code = input.code();
    u.status = input.status();
    return units.save(u);
  }

  @DeleteMapping("/tools/{id}/units/{unitId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void retireUnit(
      @PathVariable Long id, @PathVariable Long unitId, @AuthenticationPrincipal Jwt j) {
    owns(id, j);
    var u =
        units
            .findById(unitId)
            .filter(x -> x.toolId.equals(id))
            .orElseThrow(() -> new BusinessException(404, "Unidad no encontrada"));
    u.status = "RETIRADA";
    units.save(u);
  }

  @PostMapping("/tools/{id}/images")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public ImageEntity image(
      @PathVariable Long id, @Valid @RequestBody ImageInput input, @AuthenticationPrincipal Jwt j) {
    owns(id, j);
    var i = new ImageEntity();
    i.toolId = id;
    i.location = input.location();
    return images.save(i);
  }

  @DeleteMapping("/tools/{id}/images/{imageId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteImage(
      @PathVariable Long id, @PathVariable Long imageId, @AuthenticationPrincipal Jwt j) {
    owns(id, j);
    var i =
        images
            .findById(imageId)
            .filter(x -> x.toolId.equals(id))
            .orElseThrow(() -> new BusinessException(404, "Imagen no encontrada"));
    images.delete(i);
  }
}
