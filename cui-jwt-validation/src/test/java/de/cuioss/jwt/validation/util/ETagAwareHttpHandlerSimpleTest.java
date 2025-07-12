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
package de.cuioss.jwt.validation.util;

import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for ETagAwareHttpHandler LoadState enum and LoadResult record.
 * This test verifies the basic functionality without complex HTTP integration.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
class ETagAwareHttpHandlerSimpleTest {

    @Test
    void loadStateEnum() {
        // Test enum properties
        assertTrue(ETagAwareHttpHandler.LoadState.LOADED_FROM_SERVER.isDataChanged());
        assertFalse(ETagAwareHttpHandler.LoadState.CACHE_ETAG.isDataChanged());
        assertFalse(ETagAwareHttpHandler.LoadState.CACHE_CONTENT.isDataChanged());
        assertFalse(ETagAwareHttpHandler.LoadState.ERROR_WITH_CACHE.isDataChanged());
        assertTrue(ETagAwareHttpHandler.LoadState.ERROR_NO_CACHE.isDataChanged());
    }

    @Test
    void loadResultRecord() {
        ETagAwareHttpHandler.LoadResult result = new ETagAwareHttpHandler.LoadResult(
                "content", ETagAwareHttpHandler.LoadState.LOADED_FROM_SERVER);

        assertEquals("content", result.content());
        assertEquals(ETagAwareHttpHandler.LoadState.LOADED_FROM_SERVER, result.loadState());

        // Test record equality
        ETagAwareHttpHandler.LoadResult sameResult = new ETagAwareHttpHandler.LoadResult(
                "content", ETagAwareHttpHandler.LoadState.LOADED_FROM_SERVER);
        assertEquals(result, sameResult);
        assertEquals(result.hashCode(), sameResult.hashCode());
    }

    @Test
    void loadResultWithNull() {
        ETagAwareHttpHandler.LoadResult result = new ETagAwareHttpHandler.LoadResult(
                null, ETagAwareHttpHandler.LoadState.ERROR_NO_CACHE);

        assertNull(result.content());
        assertEquals(ETagAwareHttpHandler.LoadState.ERROR_NO_CACHE, result.loadState());
        assertTrue(result.loadState().isDataChanged());
    }

    @Test
    void allLoadStates() {
        // Verify all enum constants exist and have correct properties
        ETagAwareHttpHandler.LoadState[] states = ETagAwareHttpHandler.LoadState.values();
        assertEquals(5, states.length);

        // Verify specific states
        assertArrayEquals(new ETagAwareHttpHandler.LoadState[]{
                ETagAwareHttpHandler.LoadState.LOADED_FROM_SERVER,
                ETagAwareHttpHandler.LoadState.CACHE_ETAG,
                ETagAwareHttpHandler.LoadState.CACHE_CONTENT,
                ETagAwareHttpHandler.LoadState.ERROR_WITH_CACHE,
                ETagAwareHttpHandler.LoadState.ERROR_NO_CACHE
        }, states);
    }
}