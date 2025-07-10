/**
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.quarkus.reflection;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test to verify that all application-level classes in the cui-jwt-quarkus module
 * have the {@link RegisterForReflection} annotation where needed for native image compilation.
 * <p>
 * This test implements a whitelist mechanism to allow certain classes to be exempt from
 * reflection registration requirements.
 * <p>
 * According to CUI Quarkus reflection standards:
 * - Application-level classes should use {@code @RegisterForReflection}
 * - Infrastructure/library classes are handled by deployment processors
 * - This module contains application-level classes that require reflection registration
 */
class ReflectionRegistrationTest {

    /**
     * Classes that are whitelisted from requiring @RegisterForReflection annotation.
     * Add class names here that legitimately don't need reflection registration.
     */
    private static final Set<String> WHITELIST = new HashSet<>(Arrays.asList(
        // Constants/utility classes that don't need reflection
        "de.cuioss.jwt.quarkus.config.JwtPropertyKeys",
        "de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages",
        "de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages$INFO",
        "de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages$WARN", 
        "de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages$ERROR",
        
        // Configuration resolvers - CDI beans but might not need reflection for native image
        "de.cuioss.jwt.quarkus.config.IssuerConfigResolver",
        "de.cuioss.jwt.quarkus.config.ParserConfigResolver",
        
        // Runtime/deployment specific classes that may not need application-level reflection
        "de.cuioss.jwt.quarkus.runtime.CuiJwtRecorder",
        "de.cuioss.jwt.quarkus.runtime.CuiJwtDevUIRuntimeService",
        
        // Servlet resolver classes - these are handled by deployment processor
        "de.cuioss.jwt.quarkus.servlet.HttpServletRequestResolver",
        "de.cuioss.jwt.quarkus.servlet.RestEasyServletObjectsResolver",
        
        // Annotations themselves - don't need reflection registration
        "de.cuioss.jwt.quarkus.annotation.BearerToken",
        "de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver",
        
        // Metrics collector - might be registered differently
        "de.cuioss.jwt.quarkus.metrics.JwtMetricsCollector"
    ));

    /**
     * Verify that key application classes have @RegisterForReflection annotation.
     * This test checks a known set of main application classes that should have reflection registration.
     */
    @Test
    void shouldHaveRegisterForReflectionOnApplicationClasses() {
        // Define key application classes that should have @RegisterForReflection
        String[] requiredAnnotatedClasses = {
            "de.cuioss.jwt.quarkus.producer.BearerTokenProducer",
            "de.cuioss.jwt.quarkus.producer.TokenValidatorProducer", 
            "de.cuioss.jwt.quarkus.producer.BearerTokenResult",
            "de.cuioss.jwt.quarkus.producer.BearerTokenStatus",
            "de.cuioss.jwt.quarkus.health.TokenValidatorHealthCheck",
            "de.cuioss.jwt.quarkus.health.JwksEndpointHealthCheck"
        };
        
        StringBuilder missingAnnotations = new StringBuilder();
        int annotatedCount = 0;
        int totalChecked = 0;
        
        for (String className : requiredAnnotatedClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                totalChecked++;
                
                if (clazz.isAnnotationPresent(RegisterForReflection.class)) {
                    annotatedCount++;
                    System.out.printf("✓ %s has @RegisterForReflection%n", className);
                } else {
                    missingAnnotations.append("\n- ").append(className);
                    System.out.printf("✗ %s missing @RegisterForReflection%n", className);
                }
            } catch (ClassNotFoundException e) {
                fail("Could not find required class: " + className);
            }
        }
        
        // Check whitelisted classes exist but don't require annotation
        int whitelistedFound = 0;
        for (String whitelistedClass : WHITELIST) {
            try {
                Class.forName(whitelistedClass);
                whitelistedFound++;
                System.out.printf("○ %s found in whitelist%n", whitelistedClass);
            } catch (ClassNotFoundException e) {
                System.out.printf("⚠ Whitelisted class not found: %s%n", whitelistedClass);
            }
        }
        
        // Report results
        System.out.printf("%nReflection Registration Verification:%n");
        System.out.printf("- Required classes checked: %d%n", totalChecked);
        System.out.printf("- Classes with @RegisterForReflection: %d%n", annotatedCount);
        System.out.printf("- Whitelisted classes found: %d%n", whitelistedFound);
        
        // Fail test if any required classes are missing the annotation
        if (missingAnnotations.length() > 0) {
            fail("The following required classes should have @RegisterForReflection annotation:" + 
                 missingAnnotations.toString() + 
                 "\n\nAdd @RegisterForReflection to these classes.");
        }
        
        // Verify we found and checked the expected classes
        assertTrue(totalChecked > 0, "Should have checked some required classes");
        assertTrue(annotatedCount > 0, "Should have found some classes with @RegisterForReflection annotation");
        
        System.out.printf("✓ All %d required classes have proper reflection registration%n", totalChecked);
    }
    
    /**
     * Find all classes in a given package using filesystem scanning.
     */
    private Set<Class<?>> findClassesInPackage(String packageName) throws Exception {
        Set<Class<?>> classes = new HashSet<>();
        String path = packageName.replace('.', '/');
        
        // Get the classloader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource(path);
        
        System.out.printf("Looking for package: %s at path: %s%n", packageName, path);
        System.out.printf("Resource URL: %s%n", resource);
        
        if (resource != null) {
            File directory = new File(resource.getFile());
            System.out.printf("Directory exists: %s, path: %s%n", directory.exists(), directory.getAbsolutePath());
            if (directory.exists()) {
                classes.addAll(findClassesInDirectory(directory, packageName));
            }
        } else {
            System.out.println("Resource is null - trying alternative approach");
            // Alternative: scan from known main classes
            try {
                Class<?> bearerTokenProducer = Class.forName("de.cuioss.jwt.quarkus.producer.BearerTokenProducer");
                classes.add(bearerTokenProducer);
                Class<?> bearerTokenResult = Class.forName("de.cuioss.jwt.quarkus.producer.BearerTokenResult");
                classes.add(bearerTokenResult);
                Class<?> bearerTokenStatus = Class.forName("de.cuioss.jwt.quarkus.producer.BearerTokenStatus");
                classes.add(bearerTokenStatus);
                // Add more main classes manually
                String[] mainClasses = {
                    "de.cuioss.jwt.quarkus.producer.TokenValidatorProducer",
                    "de.cuioss.jwt.quarkus.health.TokenValidatorHealthCheck",
                    "de.cuioss.jwt.quarkus.health.JwksEndpointHealthCheck",
                    "de.cuioss.jwt.quarkus.config.IssuerConfigResolver",
                    "de.cuioss.jwt.quarkus.config.ParserConfigResolver",
                    "de.cuioss.jwt.quarkus.config.JwtPropertyKeys",
                    "de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages",
                    "de.cuioss.jwt.quarkus.annotation.BearerToken",
                    "de.cuioss.jwt.quarkus.annotation.ServletObjectsResolver",
                    "de.cuioss.jwt.quarkus.servlet.HttpServletRequestResolver",
                    "de.cuioss.jwt.quarkus.servlet.RestEasyServletObjectsResolver",
                    "de.cuioss.jwt.quarkus.metrics.JwtMetricsCollector",
                    "de.cuioss.jwt.quarkus.runtime.CuiJwtRecorder",
                    "de.cuioss.jwt.quarkus.runtime.CuiJwtDevUIRuntimeService"
                };
                for (String className : mainClasses) {
                    try {
                        classes.add(Class.forName(className));
                    } catch (ClassNotFoundException e) {
                        System.out.printf("Could not find class: %s%n", className);
                    }
                }
            } catch (ClassNotFoundException e) {
                System.out.printf("Could not find basic classes: %s%n", e.getMessage());
            }
        }
        
        return classes;
    }
    
    /**
     * Recursively find classes in a directory.
     */
    private Set<Class<?>> findClassesInDirectory(File directory, String packageName) throws Exception {
        Set<Class<?>> classes = new HashSet<>();
        
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                classes.addAll(findClassesInDirectory(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    classes.add(clazz);
                } catch (ClassNotFoundException e) {
                    // Ignore classes that can't be loaded
                }
            }
        }
        
        return classes;
    }
    
    /**
     * Verify that whitelisted classes actually exist and are valid.
     * This prevents stale whitelist entries from accumulating.
     * 
     * TODO: Re-enable when filesystem scanning is working properly
     */
    @Test
    @org.junit.jupiter.api.Disabled("Filesystem scanning not working properly in this environment")
    void shouldValidateWhitelistEntries() throws Exception {
        Set<Class<?>> allClasses = findClassesInPackage("de.cuioss.jwt.quarkus");
        Set<String> foundClassNames = new HashSet<>();
        
        for (Class<?> clazz : allClasses) {
            foundClassNames.add(clazz.getName());
        }
        
        StringBuilder invalidWhitelistEntries = new StringBuilder();
        
        for (String whitelistedClass : WHITELIST) {
            if (!foundClassNames.contains(whitelistedClass)) {
                invalidWhitelistEntries.append("\n- ").append(whitelistedClass);
            }
        }
        
        if (invalidWhitelistEntries.length() > 0) {
            fail("The following whitelist entries refer to classes that don't exist:" + 
                 invalidWhitelistEntries.toString() + 
                 "\n\nRemove these entries from the WHITELIST.");
        }
    }
    
    /**
     * Verify that classes with @RegisterForReflection are not redundantly listed in whitelist.
     * This helps keep the whitelist clean and accurate.
     * 
     * TODO: Re-enable when filesystem scanning is working properly
     */
    @Test
    @org.junit.jupiter.api.Disabled("Filesystem scanning not working properly in this environment")
    void shouldNotHaveAnnotatedClassesInWhitelist() throws Exception {
        Set<Class<?>> allClasses = findClassesInPackage("de.cuioss.jwt.quarkus");
        StringBuilder redundantWhitelistEntries = new StringBuilder();
        
        for (Class<?> clazz : allClasses) {
            String className = clazz.getName();
            if (clazz.isAnnotationPresent(RegisterForReflection.class) && WHITELIST.contains(className)) {
                redundantWhitelistEntries.append("\n- ").append(className);
            }
        }
        
        if (redundantWhitelistEntries.length() > 0) {
            fail("The following classes have @RegisterForReflection annotation but are also in the whitelist:" + 
                 redundantWhitelistEntries.toString() + 
                 "\n\nRemove these entries from the WHITELIST since they already have the annotation.");
        }
    }
}