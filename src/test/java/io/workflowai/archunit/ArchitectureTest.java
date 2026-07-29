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
  ArchRule domainMustNotDependOnApplication =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..application..")
          .because("domain must not depend outward on the application layer");

  @ArchTest
  ArchRule domainMustNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..")
          .because("domain must not depend on infrastructure adapters");

  @ArchTest
  ArchRule domainMustNotDependOnSpring =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..")
          .because("domain must be framework-free");

  @ArchTest
  ArchRule domainMustNotDependOnLangChain4j =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..")
          .because("AI frameworks belong in adapters only");

  @ArchTest
  ArchRule domainMustNotDependOnLangGraph4j =
      noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.bsc.langgraph4j..")
          .because("AI frameworks belong in adapters only");

  @ArchTest
  ArchRule applicationMustNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..")
          .because("application must depend on ports, not adapter implementations");

  @ArchTest
  ArchRule applicationPortsMustNotDependOnAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application.port..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..adapter..")
          .because("ports are application-owned contracts");

  @ArchTest
  ArchRule applicationMustNotDependOnLangChain4j =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..")
          .because("application should not depend on AI infrastructure");

  @ArchTest
  ArchRule applicationMustNotDependOnLangGraph4j =
      noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.bsc.langgraph4j..")
          .because("application should not depend on graph infrastructure");

  @ArchTest
  ArchRule onlyLangChain4jAdapterMayDependOnLangChain4j =
      noClasses()
          .that()
          .resideOutsideOfPackage("..adapter.out.chat..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("dev.langchain4j..")
          .because("only the chat adapters may use LangChain4j");

  @ArchTest
  ArchRule onlyRuntimeAdapterMayDependOnLangGraph4j =
      noClasses()
          .that()
          .resideOutsideOfPackage("..adapter.out.runtime.graph..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.bsc.langgraph4j..")
          .because("only the graph runtime adapter may use LangGraph4j");

  @ArchTest
  ArchRule adapterInAndOutMustNotDependOnEachOther =
      slices().matching("..adapter.(*)..")
          .should()
          .notDependOnEachOther();
}
