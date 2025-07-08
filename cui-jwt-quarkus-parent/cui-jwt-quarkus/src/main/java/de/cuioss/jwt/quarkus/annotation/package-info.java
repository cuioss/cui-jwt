/**
 * Provides CDI annotations and qualifiers for the CUI JWT Quarkus extension.
 * <p>
 * This package contains:
 * <ul>
 *   <li>CDI qualifiers for injecting JWT-related components</li>
 *   <li>Annotations for configuring JWT validation requirements</li>
 * </ul>
 * <p>
 * Key annotations:
 * <ul>
 *   <li>{@link de.cuioss.jwt.quarkus.annotation.BearerToken} - CDI qualifier for injecting validated AccessTokenContent</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
package de.cuioss.jwt.quarkus.annotation;