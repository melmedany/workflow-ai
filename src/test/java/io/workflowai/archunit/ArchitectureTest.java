package io.workflowai.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
    packages = {"io.workflowai"},
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  ArchRule domainMustNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapters..");

  @ArchTest
  ArchRule domainMustNotDependOnPersistence =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapters.persistence..");

  @ArchTest
  ArchRule applicationMustNotDependOnInfrastructure =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapters.persistence..");

  @ArchTest
  ArchRule applicationMustNotDependOnWebAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapters.rest..");

  @ArchTest
  ArchRule adaptersShouldNotDependOnEachOther =
      slices().matching("..adapters.(*)..").should().notDependOnEachOther();

  @ArchTest
  ArchRule portsMustNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..ports..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapters..");

}
