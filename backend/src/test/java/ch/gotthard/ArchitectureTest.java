package ch.gotthard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The pyramid, enforced. These are not style preferences — a failure here means a dependency now
 * points upward and the core has stopped being pure.
 *
 * <p>If one of these fails, fix the code. Do not relax the rule.
 *
 * <p>Every rule carries {@code allowEmptyShould(true)} because ArchUnit fails a rule that matches no
 * classes at all, and the layer packages are populated progressively. It is a concession to an
 * empty repository, not permission for a layer to stay empty: once a package has classes the rule
 * bites normally.
 */
@AnalyzeClasses(packages = "ch.gotthard", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    @ArchTest
    static final ArchRule core_depends_on_no_framework = noClasses()
            .that()
            .resideInAPackage("..core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "java.sql..", "javax.sql..")
            .because("the core engine must unit-test with no context, no database and no HTTP")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule core_depends_on_nothing_above_it = noClasses()
            .that()
            .resideInAPackage("..core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..service..", "..domain..", "..ai..", "..security..")
            .because("the foundation of the pyramid cannot rest on its higher layers")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllers_go_through_services = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..domain..")
            .because("the web layer talks to use cases, never to repositories directly")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_does_not_know_about_the_web = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..service..")
            .because("persistence is a lower layer than the use cases that call it")
            .allowEmptyShould(true);
}
