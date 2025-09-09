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
package de.cuioss.tools.net.http.json;

import jakarta.json.JsonString;

/**
 * Jakarta JSON API JsonString implementation backed by DSL-JSON parsing results.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class DslJsonStringAdapter implements JsonString {

    private final String value;

    public DslJsonStringAdapter(String value) {
        this.value = value != null ? value : "";
    }

    @Override
    public String getString() {
        return value;
    }

    @Override
    public CharSequence getChars() {
        return value;
    }

    @Override
    public ValueType getValueType() {
        return ValueType.STRING;
    }

    @Override
    public String toString() {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonString)) return false;
        JsonString other = (JsonString) obj;
        return value.equals(other.getString());
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}