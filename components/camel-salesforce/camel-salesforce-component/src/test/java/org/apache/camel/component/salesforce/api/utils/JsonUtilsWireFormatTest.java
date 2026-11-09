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

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what the component writes to and reads from Salesforce. The expected values were captured from the Jackson 2
 * implementation.
 */
public class JsonUtilsWireFormatTest {

    private static final LocalDateTime LOCAL = LocalDateTime.of(2024, 1, 15, 12, 30, 45);
    private static final LocalDateTime LOCAL_NANOS = LOCAL.withNano(123456789);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final ObjectMapper mapper = JsonUtils.createObjectMapper();

    private TimeZone defaultTimeZone;

    @BeforeEach
    public void fixDefaultTimeZone() {
        defaultTimeZone = TimeZone.getDefault();
        // LocalDateTime and Instant are written with the offset of the default time zone
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    }

    @AfterEach
    public void restoreDefaultTimeZone() {
        TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    public void shouldWriteZonedAndOffsetDateTimesWithMillisAndOffset() {
        assertWritten(LOCAL.atZone(PARIS), "2024-01-15T12:30:45.000+0100");
        assertWritten(LOCAL.atZone(ZoneOffset.UTC), "2024-01-15T12:30:45.000Z");
        assertWritten(LOCAL_NANOS.atZone(ZoneOffset.ofHoursMinutes(5, 30)), "2024-01-15T12:30:45.123+0530");
        assertWritten(LOCAL.atZone(PARIS).toOffsetDateTime(), "2024-01-15T12:30:45.000+0100");
        assertWritten(LOCAL.toLocalTime().atOffset(ZoneOffset.ofHours(1)), "12:30:45.000+0100");
    }

    @Test
    public void shouldWriteLocalDateTimeAndInstantInDefaultTimeZone() {
        assertWritten(LOCAL, "2024-01-15T12:30:45.000-0500");
        assertWritten(LOCAL_NANOS, "2024-01-15T12:30:45.123-0500");
        assertWritten(LOCAL.toInstant(ZoneOffset.UTC), "2024-01-15T07:30:45.000-0500");
    }

    @Test
    public void shouldWriteLocalDateAndLocalTimeAsIsoText() {
        assertWritten(LOCAL.toLocalDate(), "2024-01-15");
        assertWritten(LOCAL.toLocalTime(), "12:30:45");
        assertWritten(LocalTime.of(12, 30), "12:30:00");
        assertWritten(LOCAL_NANOS.toLocalTime(), "12:30:45.123456789");
    }

    @Test
    public void shouldWriteOtherDatesAsText() {
        assertWritten(new Date(1705321845123L), "2024-01-15T12:30:45.123+00:00");
        assertWritten(Year.of(2024), "2024");
    }

    @Test
    public void shouldKeepTheOffsetReadFromSalesforce() {
        assertRead("2024-01-15T12:30:45.000+0530", ZonedDateTime.class,
                LOCAL.atZone(ZoneOffset.ofHoursMinutes(5, 30)));
        assertRead("2024-01-15T12:30:45.000+0100", OffsetDateTime.class,
                LOCAL.atOffset(ZoneOffset.ofHours(1)));
        assertRead("2024-01-15T12:30:45.000Z", ZonedDateTime.class, LOCAL.atZone(ZoneOffset.UTC));
        assertRead("12:30:45.000+0100", OffsetTime.class, LOCAL.toLocalTime().atOffset(ZoneOffset.ofHours(1)));
    }

    @Test
    public void shouldReadOtherDates() {
        assertRead("2024-01-15T12:30:45.000+0100", Instant.class, Instant.parse("2024-01-15T11:30:45Z"));
        assertRead("2024-01-15T12:30:45.000+0530", LocalDateTime.class, LOCAL);
        assertRead("2024-01-15", LocalDate.class, LOCAL.toLocalDate());
        assertRead("2024-01-15+01:00", LocalDate.class, LOCAL.toLocalDate());
        assertRead("12:30:45", LocalTime.class, LOCAL.toLocalTime());
    }

    @Test
    public void shouldRejectOffsetWithColon() {
        assertThatThrownBy(() -> mapper.readValue("\"2024-01-15T12:30:45.000+01:00\"", ZonedDateTime.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    public void shouldKeepJackson2Defaults() throws JacksonException {
        assertThatThrownBy(() -> mapper.readValue("{\"unknown\":1}", Probe.class))
                .isInstanceOf(UnrecognizedPropertyException.class);

        // trailing tokens are not read
        assertThat(mapper.readValue("{\"zulu\":\"a\"} trailing", Probe.class).zulu).isEqualTo("a");

        // declaration order, not alphabetical
        assertThat(mapper.writeValueAsString(new Probe())).isEqualTo("{\"zulu\":null,\"alpha\":null}");
    }

    @Test
    public void shouldWriteFieldsToNullOnly() {
        final Account account = new Account();
        account.setName("x");
        account.getFieldsToNull().add("Site");

        assertThat(mapper.writeValueAsString(account))
                .isEqualTo(
                        "{\"attributes\":{\"referenceId\":null,\"type\":\"Account\",\"url\":null},\"Site\":null,\"Name\":\"x\"}");
    }

    @Test
    public void withNullSerializationShouldWriteDates() {
        final Account account = new Account();
        account.setCreatedDate(LOCAL.atZone(PARIS));

        assertThat(mapper.readTree(JsonUtils.withNullSerialization(mapper).writeValueAsString(account))
                .get("CreatedDate").asString())
                .isEqualTo("2024-01-15T12:30:45.000+0100");
    }

    @Test
    public void shouldGenerateSameSObjectSchema() throws Exception {
        assertSameSchema("account-schema.json", JsonUtils.getSObjectJsonSchema(new Account().description()));

        try (InputStream description = getClass().getResourceAsStream("/objectDescription.json")) {
            assertSameSchema("merchandise-schema.json",
                    JsonUtils.getSObjectJsonSchema(mapper.readValue(description, SObjectDescription.class)));
        }
    }

    @Test
    public void shouldGenerateGlobalObjectsSchema() throws Exception {
        try (InputStream globalObjects = getClass().getResourceAsStream("/globalObjects.json")) {
            final JsonNode schema = mapper.valueToTree(JsonUtils.getGlobalObjectsJsonSchemaAsSchema(
                    mapper.readValue(globalObjects, GlobalObjects.class)));

            assertThat(schema.get("id").asString()).isEqualTo(JsonUtils.DEFAULT_ID_PREFIX + ":GlobalObjects");
            assertThat(schema.get("oneOf").get(0).has("type")).isFalse();
        }
    }

    private void assertSameSchema(String resource, String generated) throws Exception {
        try (InputStream expected = getClass().getResourceAsStream(resource)) {
            final JsonNode expectedSchema = mapper.readTree(expected);
            final JsonNode schema = mapper.readTree(generated);

            assertThat(oneOf(schema)).isEqualTo(oneOf(expectedSchema));
            assertThat(schema.get("id")).isEqualTo(expectedSchema.get("id"));
            assertThat(schema.get("$schema")).isEqualTo(expectedSchema.get("$schema"));
            assertThat(schema.get("type")).isEqualTo(expectedSchema.get("type"));
        }
    }

    // the order of oneOf comes from a HashSet
    private static Set<JsonNode> oneOf(JsonNode schema) {
        final Set<JsonNode> entries = new HashSet<>();
        schema.get("oneOf").forEach(entries::add);
        return entries;
    }

    private void assertWritten(Object value, String expected) {
        assertThat(mapper.writeValueAsString(value)).isEqualTo('"' + expected + '"');
    }

    private <T> void assertRead(String json, Class<T> type, T expected) {
        assertThat(mapper.readValue('"' + json + '"', type)).isEqualTo(expected);
    }

    public static class Probe {
        public String zulu;
        public String alpha;
    }
}
