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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
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
import org.apache.camel.util.concurrent.ContextValue;

public final class CML extends Library {
    private static final CML INSTANCE = new CML();
    private final ContextValue<Exchange> exchange = ContextValue.newThreadLocal("DataSonnetExchange");

    private CML() {
    }

    public static CML getInstance() {
        return INSTANCE;
    }

    public ContextValue<Exchange> getExchange() {
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
                params -> formatDate(params.get(0), params.get(1), Val.Null$.MODULE$)));
        answer.put("parseDate", makeSimpleFunc(
                Arrays.asList("value", "format"),
                params -> parseDate(params.get(0), params.get(1))));

        // Formatting, parsing and arithmetic of numbers, and of dates and times as ISO-8601 strings
        answer.put("formatDateLocale", makeSimpleFunc(
                Arrays.asList("value", "format", "locale"),
                params -> formatDate(params.get(0), params.get(1), params.get(2))));
        answer.put("formatNumber", makeSimpleFunc(
                Arrays.asList("value", "format"),
                params -> formatNumber(params.get(0), params.get(1), Val.Null$.MODULE$)));
        answer.put("formatNumberLocale", makeSimpleFunc(
                Arrays.asList("value", "format", "locale"),
                params -> formatNumber(params.get(0), params.get(1), params.get(2))));
        answer.put("parseDateTime", makeSimpleFunc(
                Arrays.asList("value", "format", "type"),
                params -> parseDateTime(params.get(0), params.get(1), params.get(2))));
        answer.put("dateAdd", makeSimpleFunc(
                Arrays.asList("value", "amount"),
                params -> dateAdd(params.get(0), params.get(1))));
        answer.put("datePart", makeSimpleFunc(
                Arrays.asList("value", "part"),
                params -> datePart(params.get(0), params.get(1))));

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

    // An ISO-8601 date or time (or epoch milliseconds, as returned by parseDate) with a DateTimeFormatter pattern,
    // in the given locale (null for the default locale)
    private Val formatDate(Val value, Val format, Val locale) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        if (!(format instanceof Val.Str fmtStr)) {
            throw new IllegalArgumentException("Expected String format, got: " + format.prettyName());
        }
        TemporalAccessor temporal;
        if (value instanceof Val.Num num) {
            temporal = Instant.ofEpochMilli((long) num.value()).atZone(ZoneId.of("UTC"));
        } else if (value instanceof Val.Str valStr) {
            temporal = parseTemporal(valStr.value());
        } else {
            throw new IllegalArgumentException("Expected String or Number date value, got: " + value.prettyName());
        }
        return new Val.Str(DateTimeFormatter.ofPattern(fmtStr.value(), locale(locale)).format(temporal));
    }

    // A number with a java.text.DecimalFormat pattern, in the given locale (null for the default locale); any other
    // value is returned as is
    private Val formatNumber(Val value, Val format, Val locale) {
        if (value instanceof Val.Num num) {
            DecimalFormat decimalFormat
                    = new DecimalFormat(string(format, "format"), DecimalFormatSymbols.getInstance(locale(locale)));
            return new Val.Str(decimalFormat.format(BigDecimal.valueOf(num.value())));
        }
        return value;
    }

    private static Locale locale(Val locale) {
        if (isNull(locale)) {
            return Locale.getDefault(Locale.Category.FORMAT);
        }
        return Locale.forLanguageTag(string(locale, "locale").replace('_', '-'));
    }

    // A date or time (as an ISO-8601 string) of the given type (Date, DateTime, LocalDateTime, Time or LocalTime),
    // from a string in the given format (or ISO-8601 when the format is null) or a number of seconds since the epoch
    private Val parseDateTime(Val value, Val format, Val type) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        String kind = string(type, "type");
        TemporalAccessor parsed;
        if (value instanceof Val.Num num) {
            parsed = Instant.ofEpochSecond((long) num.value()).atZone(ZoneOffset.UTC);
        } else if (value instanceof Val.Str str && isNull(format)) {
            parsed = parseTemporal(str.value());
        } else if (value instanceof Val.Str str) {
            parsed = DateTimeFormatter.ofPattern(string(format, "format"), Locale.getDefault(Locale.Category.FORMAT))
                    .parse(str.value());
        } else {
            throw new IllegalArgumentException("Cannot convert " + value.prettyName() + " to " + kind);
        }
        return new Val.Str(switch (kind) {
            case "Date" -> LocalDate.from(parsed).toString();
            case "DateTime" -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(toZonedDateTime(parsed));
            case "LocalDateTime" -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(toLocalDateTime(parsed));
            case "Time" -> DateTimeFormatter.ISO_OFFSET_TIME.format(toOffsetTime(parsed));
            case "LocalTime" -> DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.from(parsed));
            default -> throw new IllegalArgumentException("Unknown date type: " + kind);
        });
    }

    // A date or time (as an ISO-8601 string) plus an ISO-8601 period or duration such as P1D, PT1H or -P1M
    private Val dateAdd(Val value, Val amount) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        String text = string(value, "value");
        TemporalAccessor temporal = parseTemporal(text);
        // Duration and Period parse a sign of the whole amount (-P1D) and of its parts (P-1D, PT-2H)
        String period = string(amount, "amount");
        TemporalAmount temporalAmount = period.contains("T") ? Duration.parse(period) : Period.parse(period);
        return new Val.Str(formatIso(((Temporal) temporal).plus(temporalAmount)));
    }

    // A part of a date or time (as an ISO-8601 string): year, month, day, hour, minutes, seconds, ...
    private Val datePart(Val value, Val part) {
        if (isNull(value)) {
            return Val.Null$.MODULE$;
        }
        TemporalAccessor temporal = parseTemporal(string(value, "value"));
        String name = string(part, "part");
        if ("timezone".equals(name)) {
            return temporal instanceof ZonedDateTime zdt ? new Val.Str(zdt.getZone().getId()) : Val.Null$.MODULE$;
        }
        ChronoField field = switch (name) {
            case "year" -> ChronoField.YEAR;
            case "month" -> ChronoField.MONTH_OF_YEAR;
            case "day" -> ChronoField.DAY_OF_MONTH;
            case "hour" -> ChronoField.HOUR_OF_DAY;
            case "minutes" -> ChronoField.MINUTE_OF_HOUR;
            case "seconds" -> ChronoField.SECOND_OF_MINUTE;
            case "milliseconds" -> ChronoField.MILLI_OF_SECOND;
            case "nanoseconds" -> ChronoField.NANO_OF_SECOND;
            case "dayOfWeek" -> ChronoField.DAY_OF_WEEK;
            case "dayOfYear" -> ChronoField.DAY_OF_YEAR;
            case "offsetSeconds" -> ChronoField.OFFSET_SECONDS;
            case "quarter" -> null;
            default -> throw new IllegalArgumentException("Unknown date part: " + name);
        };
        if (field == null) {
            return temporal.isSupported(IsoFields.QUARTER_OF_YEAR)
                    ? new Val.Num(temporal.get(IsoFields.QUARTER_OF_YEAR)) : Val.Null$.MODULE$;
        }
        return temporal.isSupported(field) ? new Val.Num(temporal.get(field)) : Val.Null$.MODULE$;
    }

    private static String string(Val value, String name) {
        if (value instanceof Val.Str str) {
            return str.value();
        }
        throw new IllegalArgumentException("Expected String " + name + ", got: " + value.prettyName());
    }

    // An ISO-8601 date-time with offset or zone, local date-time, date, time with offset or local time
    private static TemporalAccessor parseTemporal(String value) {
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            // try the next
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            // try the next
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            // try the next
        }
        try {
            return OffsetTime.parse(value);
        } catch (DateTimeParseException e) {
            // try the next
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Cannot parse date or time: " + value, e);
        }
    }

    private static String formatIso(TemporalAccessor temporal) {
        if (temporal instanceof ZonedDateTime zdt) {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zdt);
        }
        if (temporal instanceof LocalDateTime ldt) {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ldt);
        }
        if (temporal instanceof OffsetTime ot) {
            return DateTimeFormatter.ISO_OFFSET_TIME.format(ot);
        }
        if (temporal instanceof LocalTime lt) {
            return DateTimeFormatter.ISO_LOCAL_TIME.format(lt);
        }
        return temporal.toString();
    }

    private static ZonedDateTime toZonedDateTime(TemporalAccessor parsed) {
        if (parsed.isSupported(ChronoField.OFFSET_SECONDS)) {
            return ZonedDateTime.from(parsed);
        }
        if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) {
            return LocalDateTime.from(parsed).atZone(ZoneOffset.UTC);
        }
        return LocalDate.from(parsed).atStartOfDay(ZoneOffset.UTC);
    }

    private static LocalDateTime toLocalDateTime(TemporalAccessor parsed) {
        if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) {
            return LocalDateTime.from(parsed);
        }
        return LocalDate.from(parsed).atStartOfDay();
    }

    private static OffsetTime toOffsetTime(TemporalAccessor parsed) {
        if (parsed.isSupported(ChronoField.OFFSET_SECONDS)) {
            return OffsetTime.from(parsed);
        }
        return LocalTime.from(parsed).atOffset(ZoneOffset.UTC);
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
        TemporalAccessor parsed = formatter.parse(valStr.value());
        Instant instant;
        try {
            instant = Instant.from(parsed);
        } catch (DateTimeException e) {
            // If the format has no time component, default to start of day
            LocalDate date = LocalDate.from(parsed);
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
