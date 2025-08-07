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
package de.cuioss.jwt.quarkus.runtime;

import de.cuioss.test.juli.junit5.EnableTestLogger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Minimal unit test for {@link CuiJwtRecorder}.
 * <p>
 * This test verifies that the recorder can be instantiated without issues.
 * The recorder is currently empty as all runtime initialization is handled by CDI.
 */
@EnableTestLogger
@DisplayName("CuiJwtRecorder Tests")
class CuiJwtRecorderTest {

    @Test
    @DisplayName("Should instantiate CuiJwtRecorder without exceptions")
    void shouldInstantiateRecorderSuccessfully() {
        // Test that the recorder can be instantiated without issues
        assertDoesNotThrow(CuiJwtRecorder::new,
                "CuiJwtRecorder should be instantiable without exceptions");
    }

}