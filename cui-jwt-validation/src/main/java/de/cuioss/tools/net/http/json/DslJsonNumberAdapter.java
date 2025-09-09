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

import jakarta.json.JsonNumber;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Jakarta JSON API JsonNumber implementation backed by DSL-JSON parsing results.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class DslJsonNumberAdapter implements JsonNumber {

    private final Number value;
    private final BigDecimal bigDecimalValue;

    public DslJsonNumberAdapter(Number value) {
        this.value = value != null ? value : 0;
        this.bigDecimalValue = convertToBigDecimal(this.value);
    }

    private static BigDecimal convertToBigDecimal(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        if (number instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        // For integers, longs, etc.
        return BigDecimal.valueOf(number.longValue());
    }

    @Override
    public boolean isIntegral() {
        return bigDecimalValue.scale() <= 0;
    }

    @Override
    public int intValue() {
        return bigDecimalValue.intValue();
    }

    @Override
    public int intValueExact() {
        return bigDecimalValue.intValueExact();
    }

    @Override
    public long longValue() {
        return bigDecimalValue.longValue();
    }

    @Override
    public long longValueExact() {
        return bigDecimalValue.longValueExact();
    }

    @Override
    public BigInteger bigIntegerValue() {
        return bigDecimalValue.toBigInteger();
    }

    @Override
    public BigInteger bigIntegerValueExact() {
        return bigDecimalValue.toBigIntegerExact();
    }

    @Override
    public double doubleValue() {
        return bigDecimalValue.doubleValue();
    }

    @Override
    public BigDecimal bigDecimalValue() {
        return bigDecimalValue;
    }

    @Override
    public Number numberValue() {
        return value;
    }

    @Override
    public ValueType getValueType() {
        return ValueType.NUMBER;
    }

    @Override
    public String toString() {
        return bigDecimalValue.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonNumber)) return false;
        JsonNumber other = (JsonNumber) obj;
        return bigDecimalValue.equals(other.bigDecimalValue());
    }

    @Override
    public int hashCode() {
        return bigDecimalValue.hashCode();
    }
}