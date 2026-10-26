package com.garmentfactory.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the modular-monolith boundaries. The business modules do not exist yet, so most rules
 * pass vacuously today (allowEmptyShould). They start protecting the code as each module lands.
 */
@AnalyzeClasses(
        packages = "com.garmentfactory",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ModuleBoundaryTest {

    private static final String[] BUSINESS_MODULES = {
            "com.garmentfactory.identity..", "com.garmentfactory.customer..",
            "com.garmentfactory.catalog..", "com.garmentfactory.inventory..",
            "com.garmentfactory.pricing..", "com.garmentfactory.cart..",
            "com.garmentfactory.order..", "com.garmentfactory.payment..",
            "com.garmentfactory.customprint..", "com.garmentfactory.quotation..",
            "com.garmentfactory.production..", "com.garmentfactory.quality..",
            "com.garmentfactory.shipping..", "com.garmentfactory.returns..",
            "com.garmentfactory.notification..", "com.garmentfactory.reporting..",
            "com.garmentfactory.audit..", "com.garmentfactory.storage.."
    };

    @ArchTest
    static final ArchRule controllersDoNotTouchRepositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule sharedKernelDoesNotDependOnBusinessModules = noClasses()
            .that().resideInAPackage("com.garmentfactory.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(BUSINESS_MODULES)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles = slices()
            .matching("com.garmentfactory.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);
}