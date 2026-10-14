package com.renttool.infrastructure;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas que Maven no puede expresar. Maven ya impide que domain vea application o frameworks, y que
 * application vea infrastructure; aquí se vigila el uso de puertos y el aislamiento de contextos.
 */
@AnalyzeClasses(packages = "com.renttool", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {
  @ArchTest
  static final ArchRule webUsesInputPortsOnly =
      noClasses()
          .that()
          .resideInAPackage("com.renttool.infrastructure.*.web..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.renttool.application.*.service..",
              "com.renttool.application.*.port.out..",
              "com.renttool.infrastructure.*.persistence..")
          .because("los controllers invocan casos de uso, no servicios ni repositorios");

  @ArchTest
  static final ArchRule persistenceIsPrivateToItsAdapter =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.renttool.infrastructure.*.persistence..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.renttool.infrastructure.*.persistence..")
          .because("solo el adaptador de persistencia conoce las entidades JPA");

  @ArchTest
  static final ArchRule inventoryIsIndependentOfIdentity =
      noClasses()
          .that()
          .resideInAPackage("com.renttool.*.inventory..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.renttool.*.identity..");

  @ArchTest
  static final ArchRule identityIsIndependentOfInventory =
      noClasses()
          .that()
          .resideInAPackage("com.renttool.*.identity..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.renttool.*.inventory..");
}
