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
package org.apache.camel.dsl.jbang.run.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliTimesTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // times around the changes of daylight saving time, and zones with half an hour
    private static final List<String> TIMES = List.of(
            "2026-03-29T00:59:59Z", "2026-03-29T01:00:00Z", "2026-10-25T00:59:59Z", "2026-10-25T01:00:00Z",
            "2026-03-08T06:59:59Z", "2026-03-08T07:00:00Z", "2026-11-01T05:59:59Z", "2026-11-01T06:00:00Z",
            "2026-04-04T14:59:59Z", "2026-04-04T15:00:00Z", "2026-07-01T12:00:00Z", "1999-12-31T23:59:59Z");

    @Test
    void sameAsTheZoneRules() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zone : List.of("Europe/Copenhagen", "America/New_York", "Asia/Kolkata", "Australia/Lord_Howe",
                    "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                ZoneId id = ZoneId.of(zone);
                for (String time : TIMES) {
                    long millis = Instant.parse(time).toEpochMilli();
                    assertThat(CliTimes.format(FORMAT, millis)).as(zone + " " + time)
                            .isEqualTo(FORMAT.withZone(id).format(Instant.ofEpochMilli(millis)));
                    assertThat(CliTimes.localDateTime(millis)).as(zone + " " + time)
                            .isEqualTo(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), id));
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void parseTheTextOfAZonedDateTime() {
        ZonedDateTime time = ZonedDateTime.of(2026, 11, 16, 15, 38, 49, 123_000_000, ZoneId.of("Europe/Copenhagen"));
        long expected = time.toInstant().toEpochMilli();

        assertThat(CliTimes.parseEpochMillis(time.toString())).isEqualTo(expected);
        assertThat(CliTimes.parseEpochMillis("2026-11-16T15:38:49.123+01:00[Europe/Copenhagen]")).isEqualTo(expected);
        assertThat(CliTimes.parseEpochMillis("2026-11-16T15:38:49.123+01:00")).isEqualTo(expected);
        assertThat(CliTimes.parseEpochMillis("2026-11-16T14:38:49.123Z")).isEqualTo(expected);
        assertThat(CliTimes.parseEpochMillis("2026-11-16T14:38:49.123Z[UTC]")).isEqualTo(expected);
    }

    @Test
    void notADateTime() {
        assertThatThrownBy(() -> CliTimes.parseEpochMillis("yesterday")).isInstanceOf(DateTimeParseException.class);
    }
}
