/*
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
package de.cuioss.jwt.quarkus.integration.token;

/**
 * Exception thrown when token fetching from Keycloak fails.
 * This exception wraps the underlying cause and provides context about the failure.
 */
public class TokenFetchException extends Exception {

    /**
     * Creates a new TokenFetchException with a message.
     *
     * @param message the detail message
     */
    public TokenFetchException(String message) {
        super(message);
    }

    /**
     * Creates a new TokenFetchException with a message and cause.
     *
     * @param message the detail message
     * @param cause the underlying cause
     */
    public TokenFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}