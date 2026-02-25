package io.mars.lite;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static JavaClasses allClasses;

    @BeforeAll
    static void importClasses() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.mars.lite");
    }

    // === LAYER BOUNDARY RULES ===
    //
    // NOTE: Domain is intentionally ALLOWED to use org.springframework.stereotype.Service
    // and org.springframework.transaction.annotation.Transactional.
    // Only infrastructure-specific Spring imports (JPA, Kafka, Web, Servlet) are blocked.

    @Test
    @DisplayName("Domain should not depend on infrastructure, api, or configuration")
    void domainShouldNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..infrastructure..", "..api..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain should not use JPA annotations")
    void domainShouldNotUseJpa() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain should not use Kafka annotations")
    void domainShouldNotUseKafka() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.kafka..", "org.apache.kafka..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Domain should not use Spring Web or Servlet annotations")
    void domainShouldNotUseWebAnnotations() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                .check(allClasses);
    }

    @Test
    @DisplayName("API should not access infrastructure persistence directly")
    void apiShouldNotAccessRepositoriesDirectly() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure.persistence..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Infrastructure should not depend on API layer")
    void infrastructureShouldNotDependOnApi() {
        noClasses()
                .that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat()
                .resideInAPackage("..api..")
                .check(allClasses);
    }

    // === STRUCTURAL RULES ===

    @Test
    @DisplayName("Repository interfaces in domain should be interfaces (ports)")
    void repositoryInterfacesInDomainShouldBeInterfaces() {
        classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().resideInAPackage("..domain..")
                .should().beInterfaces()
                .check(allClasses);
    }

    @Test
    @DisplayName("Event publisher interfaces in domain should be interfaces (ports)")
    void eventPublisherInterfacesInDomainShouldBeInterfaces() {
        classes()
                .that().haveSimpleNameEndingWith("EventPublisher")
                .and().resideInAPackage("..domain..")
                .should().beInterfaces()
                .check(allClasses);
    }

    @Test
    @DisplayName("Repository implementations should reside in infrastructure.persistence")
    void repositoryImplsShouldResideInInfrastructurePersistence() {
        classes()
                .that().haveSimpleNameEndingWith("RepositoryImpl")
                .should().resideInAPackage("..infrastructure.persistence..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Use cases should reside in domain.usecase package")
    void useCasesShouldBeInDomainUsecasePackage() {
        classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage("..domain.usecase..")
                .check(allClasses);
    }

    @Test
    @DisplayName("Use cases should be annotated with @Service")
    void useCasesShouldBeAnnotatedWithService() {
        classes()
                .that().resideInAPackage("..domain.usecase..")
                .and().areNotInterfaces()
                .and().haveSimpleNameEndingWith("UseCase")
                .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                .check(allClasses);
    }

    @Test
    @DisplayName("API controllers should not access infrastructure directly")
    void apiShouldNotAccessInfrastructureDirectly() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .check(allClasses);
    }
}
