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
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class SalesforceTimeFormatsTest {

    @XStreamAlias("Dto")
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

            final DateTransferObject<?> dto = (DateTransferObject<?>)obj;

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

    @Parameter(0)
    public DateTransferObject<?> dto;

    @Parameter(1)
    public String json;

    @Parameter(3)
    public Class<?> parameterType;

    @Parameter(2)
    public String xml;

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    private final XStream xStream = XStreamUtils.createXStream(DateTransferObject.class);

    @Test
    public void shouldDeserializeJson() throws IOException {
        final JavaType javaType = TypeFactory.defaultInstance().constructParametricType(DateTransferObject.class, parameterType);

        final DateTransferObject<?> deserialized = objectMapper.readerFor(javaType).readValue(json);

        assertDeserializationResult(deserialized);
    }

    @Test
    public void shouldDeserializeXml() {
        xStream.addDefaultImplementation(parameterType, Object.class);

        final DateTransferObject<?> deserialized = (DateTransferObject<?>)xStream.fromXML(xml);

        assertDeserializationResult(deserialized);
    }

    @Test
    public void shouldSerializeJson() throws JsonProcessingException {
        assertThat(objectMapper.writeValueAsString(dto)).isEqualTo(json);
    }

    @Test
    public void shouldSerializeXml() {
        assertThat(xStream.toXML(dto)).isEqualTo(xml);
    }

    private void assertDeserializationResult(final DateTransferObject<?> deserialized) {
        if (dto.value instanceof ZonedDateTime) {
            // Salesforce expresses time in UTC+offset (ISO-8601 , with this we
            // loose time zone information
            final ZonedDateTime dtoValue = (ZonedDateTime)dto.value;
            final ZonedDateTime deserializedValue = (ZonedDateTime)deserialized.value;

            assertThat(deserializedValue).isEqualTo(dtoValue.withFixedOffsetZone());
        } else {
            assertThat(deserialized.value).isEqualTo(dto.value);
        }
    }

    @Parameters
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
                             dto(zonedDateTime.toOffsetDateTime().withOffsetSameInstant(ZoneOffset.of("-7")), "2007-03-19T03:54:57.000-0700"), // 6
                             dto(zonedDateTime, "2007-03-19T10:54:57.000Z"), // 7
                             dto(zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Kolkata")), "2007-03-19T16:24:57.000+0530"), // 8
                             dto(zonedDateTime.withZoneSameInstant(ZoneId.of("+3")), "2007-03-19T13:54:57.000+0300"), // 9
                             dto(instant, instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX"))), // 10
                             dto(ZonedDateTime.of(2018, 03, 22, 9, 58, 8, 5000000, ZoneId.of("Z")), "2018-03-22T09:58:08.005Z"), // 11
                             dto(OffsetTime.of(LocalTime.MIDNIGHT, ZoneOffset.UTC), "00:00:00.000Z"), // 12
                             dto(OffsetTime.of(12, 13, 14, 7000000, ZoneOffset.UTC), "12:13:14.007Z") // 13
        );
    }

    private static Object[] dto(final Object value, final String serialized) {
        final DateTransferObject<?> dto = new DateTransferObject<>(value);

        final String json = format(JSON_FMT, serialized);

        final String xml = format(XML_FMT, serialized);

        return new Object[] {dto, json, xml, value.getClass()};
    }
}
