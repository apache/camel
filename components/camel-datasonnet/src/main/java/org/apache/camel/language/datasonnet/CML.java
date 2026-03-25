/*
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
package org.apache.camel.language.datasonnet;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.Document;
import com.datasonnet.document.MediaTypes;
import com.datasonnet.header.Header;
import com.datasonnet.jsonnet.Materializer;
import com.datasonnet.jsonnet.Val;
import com.datasonnet.spi.DataFormatService;
import com.datasonnet.spi.Library;
import com.datasonnet.spi.PluginException;
import org.apache.camel.Exchange;

public final class CML extends Library {
    private static final CML INSTANCE = new CML();
    private final ThreadLocal<Exchange> exchange = new ThreadLocal<>();

    private CML() {
    }

    public static CML getInstance() {
        return INSTANCE;
    }

    public ThreadLocal<Exchange> getExchange() {
        return exchange;
    }

    @Override
    public String namespace() {
        return "cml";
    }

    @Override
    public Set<String> libsonnets() {
        return Collections.emptySet();
    }

    @Override
    public Map<String, Val.Func> functions(DataFormatService dataFormats, Header header) {
        Map<String, Val.Func> answer = new HashMap<>();

        // Existing Camel exchange access functions
        answer.put("properties", makeSimpleFunc(
                Collections.singletonList("key"),
                params -> properties(params.get(0))));
        answer.put("header", makeSimpleFunc(
                Collections.singletonList("key"),
                params -> header(params.get(0), dataFormats)));
        answer.put("variable", makeSimpleFunc(
                Collections.singletonList("key"),
                params -> variable(params.get(0), dataFormats)));
        answer.put("exchangeProperty", makeSimpleFunc(
                Collections.singletonList("key"),
                params -> exchangeProperty(params.get(0), dataFormats)));

        // Null handling functions
        answer.put("defaultVal", makeSimpleFunc(
                Arrays.asList("value", "fallback"),
                params -> defaultVal(params.get(0), params.get(1))));
        answer.put("isEmpty", makeSimpleFunc(
                Collections.singletonList("value"),
                params -> isEmpty(params.get(0))));

        // Type coercion functions
        answer.put("toInteger", makeSimpleFunc(
                Collections.singletonList("value"),
                params -> toInteger(params.get(0))));
        answer.put("toDecimal", makeSimpleFunc(
                Collections.singletonList("value"),
                params -> toDecimal(params.get(0))));
        answer.put("toBoolean", makeSimpleFunc(
                Collections.singletonList("value"),
                params -> toBoolean(params.get(0))));

        // Date/time functions
        answer.put("now", makeSimpleFunc(
                Collections.emptyList(),
                params -> now()));
        answer.put("nowFmt", makeSimpleFunc(
                Collections.singletonList("format"),
                params -> nowFmt(params.get(0))));
        answer.put("formatDate", makeSimpleFunc(
                Arrays.asList("value", "format"),
                params -> formatDate(params.get(0), params.get(1))));
        answer.put("parseDate", makeSimpleFunc(
                Arrays.asList("value", "format"),
                params -> parseDate(params.get(0), params.get(1))));

        // Math functions
        answer.put("sqrt", makeSimpleFunc(
                Collections.singletonList("value"),
                params -> sqrt(params.get(0))));

        // Utility functions
        answer.put("uuid", makeSimpleFunc(
                Collections.emptyList(),
                params -> uuid()));
        answer.put("typeOf", makeSimpleFunc(
                Collections.singletonList("value"),
                params -> typeOf(params.get(0))));

        return answer;
    }

    @Override
    public Map<String, Val.Obj> modules(DataFormatService dataFormats, Header header) {
        return Collections.emptyMap();
    }

    // ---- Existing exchange access functions ----

    private Val properties(Val key) {
        if (key instanceof Val.Str str) {
            return new Val.Str(
                    exchange.get().getContext().resolvePropertyPlaceholders("{{" + str.value() + "}}"));
        }
        throw new IllegalArgumentException("Expected String got: " + key.prettyName());
    }

    private Val header(Val key, DataFormatService dataformats) {
        if (key instanceof Val.Str str) {
            return valFrom(exchange.get().getMessage().getHeader(str.value()), dataformats);
        }
        throw new IllegalArgumentException("Expected String got: " + key.prettyName());
    }

    private Val variable(Val key, DataFormatService dataformats) {
        if (key instanceof Val.Str str) {
            return valFrom(exchange.get().getVariable(str.value()), dataformats);
        }
        throw new IllegalArgumentException("Expected String got: " + key.prettyName());
    }

    private Val exchangeProperty(Val key, DataFormatService dataformats) {
        if (key instanceof Val.Str str) {
            return valFrom(exchange.get().getProperty(str.value()), dataformats);
        }
        throw new IllegalArgumentException("Expected String got: " + key.prettyName());
    }

    // ---- Null handling functions ----

    private Val defaultVal(Val value, Val fallback) {
        if (isNull(value)) {
            return fallback;
        }
        return value;
    }

    private Val isEmpty(Val value) {
        if (isNull(value)) {
            return Val.True$.MODULE$;
        }
        if (value instanceof Val.Str str) {
            return str.value().isEmpty() ? Val.True$.MODULE$ : Val.False$.MODULE$;
        }
        if (value instanceof Val.Arr arr) {
            return arr.value().isEmpty() ? Val.True$.MODULE$ : Val.False$.MODULE$;
        }
        return Val.False$.MODULE$;
    }

    // ---- Type coercion functions ----

    private Val toInteger(Val value) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        if (value instanceof Val.Num num) {
            return new Val.Num((int) num.value());
        }
        if (value instanceof Val.Str str) {
            return new Val.Num(Integer.parseInt(str.value().trim()));
        }
        if (value instanceof Val.Bool) {
            return new Val.Num(value == Val.True$.MODULE$ ? 1 : 0);
        }
        throw new IllegalArgumentException("Cannot convert " + value.prettyName() + " to integer");
    }

    private Val toDecimal(Val value) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        if (value instanceof Val.Num num) {
            return value;
        }
        if (value instanceof Val.Str str) {
            return new Val.Num(new BigDecimal(str.value().trim()).doubleValue());
        }
        throw new IllegalArgumentException("Cannot convert " + value.prettyName() + " to decimal");
    }

    private Val toBoolean(Val value) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        if (value instanceof Val.Bool) {
            return value;
        }
        if (value instanceof Val.Str str) {
            String s = str.value().trim().toLowerCase();
            return switch (s) {
                case "true", "1", "yes" -> Val.True$.MODULE$;
                case "false", "0", "no" -> Val.False$.MODULE$;
                default -> throw new IllegalArgumentException("Cannot convert string '" + s + "' to boolean");
            };
        }
        if (value instanceof Val.Num num) {
            return num.value() != 0 ? Val.True$.MODULE$ : Val.False$.MODULE$;
        }
        throw new IllegalArgumentException("Cannot convert " + value.prettyName() + " to boolean");
    }

    // ---- Date/time functions ----

    private Val now() {
        return new Val.Str(Instant.now().toString());
    }

    private Val nowFmt(Val format) {
        if (!(format instanceof Val.Str str)) {
            throw new IllegalArgumentException("Expected String format, got: " + format.prettyName());
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(str.value());
        return new Val.Str(ZonedDateTime.now(ZoneId.of("UTC")).format(formatter));
    }

    private Val formatDate(Val value, Val format) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        if (!(value instanceof Val.Str valStr)) {
            throw new IllegalArgumentException("Expected String date value, got: " + value.prettyName());
        }
        if (!(format instanceof Val.Str fmtStr)) {
            throw new IllegalArgumentException("Expected String format, got: " + format.prettyName());
        }
        ZonedDateTime dateTime = parseToZonedDateTime(valStr.value());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(fmtStr.value());
        return new Val.Str(dateTime.format(formatter));
    }

    private Val parseDate(Val value, Val format) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        if (!(value instanceof Val.Str valStr)) {
            throw new IllegalArgumentException("Expected String date value, got: " + value.prettyName());
        }
        if (!(format instanceof Val.Str fmtStr)) {
            throw new IllegalArgumentException("Expected String format, got: " + format.prettyName());
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(fmtStr.value()).withZone(ZoneId.of("UTC"));
        java.time.temporal.TemporalAccessor parsed = formatter.parse(valStr.value());
        Instant instant;
        try {
            instant = Instant.from(parsed);
        } catch (java.time.DateTimeException e) {
            // If the format has no time component, default to start of day
            java.time.LocalDate date = java.time.LocalDate.from(parsed);
            instant = date.atStartOfDay(ZoneId.of("UTC")).toInstant();
        }
        return new Val.Num(instant.toEpochMilli());
    }

    // ---- Math functions ----

    private Val sqrt(Val value) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        if (value instanceof Val.Num num) {
            return new Val.Num(Math.sqrt(num.value()));
        }
        throw new IllegalArgumentException("Cannot compute sqrt of " + value.prettyName());
    }

    // ---- Utility functions ----

    private Val uuid() {
        return new Val.Str(UUID.randomUUID().toString());
    }

    private Val typeOf(Val value) {
        if (isNull(value)) {
            return new Val.Str("null");
        }
        if (value instanceof Val.Str) {
            return new Val.Str("string");
        }
        if (value instanceof Val.Num) {
            return new Val.Str("number");
        }
        if (value instanceof Val.Bool) {
            return new Val.Str("boolean");
        }
        if (value instanceof Val.Arr) {
            return new Val.Str("array");
        }
        if (value instanceof Val.Obj) {
            return new Val.Str("object");
        }
        if (value instanceof Val.Func) {
            return new Val.Str("function");
        }
        return new Val.Str("unknown");
    }

    // ---- Helper methods ----

    private static boolean isNull(Val value) {
        return value == null || value instanceof Val.Null$;
    }

    private static ZonedDateTime parseToZonedDateTime(String value) {
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            // Try as instant
            try {
                return Instant.parse(value).atZone(ZoneId.of("UTC"));
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Cannot parse date: " + value, e2);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Val valFrom(Object obj, DataFormatService dataformats) {
        Document<?> doc;
        if (obj instanceof Document<?> document) {
            doc = document;
        } else {
            doc = new DefaultDocument<>(obj, MediaTypes.APPLICATION_JAVA);
        }

        try {
            return Materializer.reverse(dataformats.mandatoryRead(doc));
        } catch (PluginException e) {
            throw new IllegalStateException(e);
        }
    }
}
