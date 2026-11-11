package com.tesoro.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Guards the hexagonal layout: dependencies only point inwards. */
@AnalyzeClasses(packages = "com.tesoro", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that()
            .resideInAPackage("com.tesoro.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "org.hibernate..",
                    "com.fasterxml..",
                    "com.tesoro.application..",
                    "com.tesoro.adapter..",
                    "com.tesoro.config..");

    @ArchTest
    static final ArchRule applicationDoesNotKnowAdaptersOrInfrastructure = noClasses()
            .that()
            .resideInAPackage("com.tesoro.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.tesoro.adapter..",
                    "com.tesoro.config..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "org.springframework.web..",
                    "org.springframework.jdbc..",
                    "org.springframework.security..");

    @ArchTest
    static final ArchRule inboundAdaptersDoNotUseOutboundAdapters = noClasses()
            .that()
            .resideInAPackage("com.tesoro.adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.tesoro.adapter.out..");

    @ArchTest
    static final ArchRule outboundAdaptersDoNotUseInboundAdapters = noClasses()
            .that()
            .resideInAPackage("com.tesoro.adapter.out..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.tesoro.adapter.in..");

    @ArchTest
    static final ArchRule controllersOnlyTalkToUseCasePorts = noClasses()
            .that()
            .resideInAPackage("com.tesoro.adapter.in.web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.tesoro.application.service..", "com.tesoro.application.port.out..");
}
