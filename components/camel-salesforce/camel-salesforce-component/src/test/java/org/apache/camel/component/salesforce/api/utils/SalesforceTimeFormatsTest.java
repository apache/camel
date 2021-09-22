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
package org.apache.camel.component.salesforce.api.utils;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class SalesforceTimeFormatsTest {

    public static class DateTransferObject<T> {

        private T value;

        public DateTransferObject() {
        }

        public DateTransferObject(final T value) {
            this.value = value;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof DateTransferObject)) {
                return false;
            }

            final DateTransferObject<?> dto = (DateTransferObject<?>) obj;

            return Objects.equals(value, dto.value);
        }

        public T getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, value);
        }

        public void setValue(final T value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static final String JSON_FMT = "{\"value\":\"%s\"}";

    private static final String XML_FMT = "<Dto><value>%s</value></Dto>";

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    @ParameterizedTest
    @MethodSource("cases")
    public void shouldDeserializeJson(DateTransferObject<?> dto, String json, Class<?> parameterType)
            throws IOException {
        final JavaType javaType
                = TypeFactory.defaultInstance().constructParametricType(DateTransferObject.class, parameterType);

        final DateTransferObject<?> deserialized = objectMapper.readerFor(javaType).readValue(json);

        assertDeserializationResult(dto, deserialized);
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void shouldSerializeJson(DateTransferObject<?> dto, String json, Class<?> parameterType)
            throws JsonProcessingException {
        String actual = objectMapper.writeValueAsString(dto).replaceAll("000\\+00:00", "000+0000");
        String expected = json;
        assertThat(actual).isEqualTo(expected);
    }

    private void assertDeserializationResult(DateTransferObject<?> dto, final DateTransferObject<?> deserialized) {
        if (dto.value instanceof ZonedDateTime) {
            // Salesforce expresses time in UTC+offset (ISO-8601 , with this we
            // loose time zone information
            final ZonedDateTime dtoValue = (ZonedDateTime) dto.value;
            final ZonedDateTime deserializedValue = (ZonedDateTime) deserialized.value;
            String actual
                    = deserializedValue.format(DateTimeFormatter.ISO_INSTANT).replaceAll("000\\+00:00", "000+0000");
            String expected = dtoValue.format(DateTimeFormatter.ISO_INSTANT);

            assertThat(actual).isEqualTo(expected);
        } else {
            assertThat(deserialized.value).isEqualTo(dto.value);
        }
    }

    public static Iterable<Object[]> cases() {
        final LocalDate localDate = LocalDate.of(2007, 03, 19);
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate.atTime(10, 54, 57), ZoneId.of("Z"));
        final Instant instant = zonedDateTime.toInstant();

        final String zone = DateTimeFormatter.ofPattern("XX").format(zonedDateTime.withZoneSameLocal(ZoneId.systemDefault()));

        return Arrays.asList(//
                dto(Date.from(instant), "2007-03-19T10:54:57.000+0000"), // 0
                dto(Date.from(localDate.atStartOfDay().toInstant(ZoneOffset.UTC)), "2007-03-19T00:00:00.000+0000"), // 1
                dto(localDate, "2007-03-19"), // 2
                dto(zonedDateTime.toLocalDateTime(), "2007-03-19T10:54:57.000" + zone), // 3
                dto(zonedDateTime.toOffsetDateTime(), "2007-03-19T10:54:57.000Z"), // 4
                dto(zonedDateTime.toOffsetDateTime(), "2007-03-19T10:54:57.000Z"), // 5
                dto(zonedDateTime.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.of("-7")),
                        "2007-03-19T03:54:57.000-0700"), // 6
                dto(zonedDateTime, "2007-03-19T10:54:57.000Z"), // 7
                dto(zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Kolkata")), "2007-03-19T16:24:57.000+0530"), // 8
                dto(zonedDateTime.withZoneSameInstant(ZoneId.of("+3")), "2007-03-19T13:54:57.000+0300"), // 9
                dto(instant,
                        instant.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"))), // 10
                dto(ZonedDateTime.of(2018, 03, 22, 9, 58, 8, 5000000, ZoneId.of("Z")), "2018-03-22T09:58:08.005Z"), // 11
                dto(OffsetTime.of(LocalTime.MIDNIGHT, ZoneOffset.UTC), "00:00:00.000Z"), // 12
                dto(OffsetTime.of(12, 13, 14, 7000000, ZoneOffset.UTC), "12:13:14.007Z") // 13
        );
    }

    private static Object[] dto(final Object value, final String serialized) {
        final DateTransferObject<?> dto = new DateTransferObject<>(value);

        final String json = format(JSON_FMT, serialized);

        return new Object[] { dto, json, value.getClass() };
    }
}
