package com.renttool.infrastructure.inventory.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ToolJpaRepository
    extends JpaRepository<ToolEntity, Long>, JpaSpecificationExecutor<ToolEntity> {}
