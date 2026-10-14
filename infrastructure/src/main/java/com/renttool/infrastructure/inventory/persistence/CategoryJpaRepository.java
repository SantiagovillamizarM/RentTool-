package com.renttool.infrastructure.inventory.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {}
