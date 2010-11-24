/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dataformat.bindy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Locale;

import org.apache.camel.dataformat.bindy.format.BigDecimalFormat;
import org.apache.camel.dataformat.bindy.format.BigIntegerFormat;
import org.apache.camel.dataformat.bindy.format.ByteFormat;
import org.apache.camel.dataformat.bindy.format.BytePatternFormat;
import org.apache.camel.dataformat.bindy.format.CharacterFormat;
import org.apache.camel.dataformat.bindy.format.DatePatternFormat;
import org.apache.camel.dataformat.bindy.format.DoubleFormat;
import org.apache.camel.dataformat.bindy.format.DoublePatternFormat;
import org.apache.camel.dataformat.bindy.format.FloatFormat;
import org.apache.camel.dataformat.bindy.format.FloatPatternFormat;
import org.apache.camel.dataformat.bindy.format.IntegerFormat;
import org.apache.camel.dataformat.bindy.format.IntegerPatternFormat;
import org.apache.camel.dataformat.bindy.format.LongFormat;
import org.apache.camel.dataformat.bindy.format.LongPatternFormat;
import org.apache.camel.dataformat.bindy.format.ShortFormat;
import org.apache.camel.dataformat.bindy.format.ShortPatternFormat;
import org.apache.camel.dataformat.bindy.format.StringFormat;

/**
 * Factory to return {@link Format} classes for a given type.
 */
public final class FormatFactory {

    private FormatFactory() {
    }

    /**
     * Retrieves the format to use for the given type
     * 
     * @param clazz represents the type of the format (String, Integer, Byte)
     * @param pattern is the pattern to be used during the formating of the data
     * @param locale optional locale for NumberFormat and DateFormat parsing.
     * @param precision optional scale for BigDecimal parsing.
     * @return Format the formatter
     * @throws IllegalArgumentException if not suitable formatter is found
     */
    public static Format<?> getFormat(Class<?> clazz, String pattern, String locale, int precision) throws Exception {
        if (clazz == byte.class || clazz == Byte.class) {
            return pattern != null ? new BytePatternFormat(pattern, getLocale(locale)) : new ByteFormat();

        } else if (clazz == short.class || clazz == Short.class) {
            return pattern != null ? new ShortPatternFormat(pattern, getLocale(locale)) : new ShortFormat();

        } else if (clazz == int.class || clazz == Integer.class) {
            return pattern != null ? new IntegerPatternFormat(pattern, getLocale(locale)) : new IntegerFormat();

        } else if (clazz == long.class || clazz == Long.class) {
            return pattern != null ? new LongPatternFormat(pattern, getLocale(locale)) : new LongFormat();

        } else if (clazz == float.class || clazz == Float.class) {
            return pattern != null ? new FloatPatternFormat(pattern, getLocale(locale)) : new FloatFormat();

        } else if (clazz == double.class || clazz == Double.class) {
            return pattern != null ? new DoublePatternFormat(pattern, getLocale(locale)) : new DoubleFormat();

        } else if (clazz == BigDecimal.class) {
            return new BigDecimalFormat(precision);

        } else if (clazz == BigInteger.class) {
            return new BigIntegerFormat();

        } else if (clazz == String.class) {
            return new StringFormat();

        } else if (clazz == Date.class) {
            return new DatePatternFormat(pattern, getLocale(locale));

        } else if (clazz == char.class || clazz == Character.class) {
            return new CharacterFormat();

        } else {
            throw new IllegalArgumentException("Can not find a suitable formatter for the type: " + clazz.getCanonicalName());
        }
    }

    private static Locale getLocale(String locale) {
        if (locale != null && !locale.isEmpty()) {
            String[] result = locale.split("-");
            if (result.length <= 2) {
                return result.length == 1 ? new Locale(result[0]) : new Locale(result[0], result[1]);
            }
        }
        return null;
    }

}
