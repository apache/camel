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
package org.apache.camel.component.servicenow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceNowMapperTest {

    public static class Dates {
        public LocalDate date;
        public LocalTime time;
        public LocalDateTime dateTime;
    }

    private static Dates dates() {
        Dates dates = new Dates();
        dates.date = LocalDate.of(1990, 5, 17);
        dates.time = LocalTime.of(10, 30);
        dates.dateTime = LocalDateTime.of(1990, 5, 17, 10, 30);
        return dates;
    }

    @Test
    void dateOptionsApplyWhenWritingAndReading() {
        ServiceNowConfiguration configuration = new ServiceNowConfiguration();
        configuration.setDateFormat("dd/MM/yyyy");
        configuration.setTimeFormat("HH.mm");
        configuration.setDateTimeFormat("dd/MM/yyyy HH.mm");
        ObjectMapper mapper = configuration.getOrCreateMapper();

        String json = mapper.writeValueAsString(dates());
        assertEquals("{\"date\":\"17/05/1990\",\"time\":\"10.30\",\"dateTime\":\"17/05/1990 10.30\"}", json);

        Dates read = mapper.readValue(json, Dates.class);
        assertEquals(dates().date, read.date);
        assertEquals(dates().time, read.time);
        assertEquals(dates().dateTime, read.dateTime);
    }

    @Test
    void defaultDateFormats() {
        ObjectMapper mapper = new ServiceNowConfiguration().getOrCreateMapper();

        assertEquals("{\"date\":\"1990-05-17\",\"time\":\"10:30:00\",\"dateTime\":\"1990-05-17 10:30:00\"}",
                mapper.writeValueAsString(dates()));
    }

    @Test
    void nullsAreLeftOutOfBeansAndMaps() {
        ObjectMapper mapper = new ServiceNowConfiguration().getOrCreateMapper();

        Dates dates = new Dates();
        dates.date = LocalDate.of(1990, 5, 17);
        assertEquals("{\"date\":\"1990-05-17\"}", mapper.writeValueAsString(dates));

        Map<String, String> map = new HashMap<>();
        map.put("key", null);
        assertEquals("{}", mapper.writeValueAsString(map));
    }

    @Test
    void unknownPropertiesAreIgnored() {
        ObjectMapper mapper = new ServiceNowConfiguration().getOrCreateMapper();

        Dates read = mapper.readValue("{\"date\":\"1990-05-17\",\"unknown\":1}", Dates.class);
        assertEquals(LocalDate.of(1990, 5, 17), read.date);
    }
}
