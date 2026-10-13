package com.renttool.inventory.infrastructure;

public interface JpaTools
    extends org.springframework.data.jpa.repository.JpaRepository<ToolEntity, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<ToolEntity> {}
