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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTabLogDataJsonTest {

    private static List<LogEntry> sampleLog() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(LogTab.parseLogLine(
                "2026-09-08 18:34:00.100  INFO 8919 --- [main] org.apache.camel.main.MainSupport : Camel started"));
        entries.add(LogTab.parseLogLine(
                "2026-09-08 18:34:10.429  WARN 8919 --- [mqtt-source] o.a.c.c.paho.mqtt5.PahoMqtt5Consumer : Error processing exchange"));
        entries.add(LogTab.parseLogLine(
                "org.apache.camel.RuntimeCamelException: org.apache.camel.TypeConversionException: byte[] to JsonNode"));
        entries.add(
                LogTab.parseLogLine("        at org.apache.camel.language.jq.JqExpression.evaluate(JqExpression.java:182)"));
        entries.add(
                LogTab.parseLogLine("Caused by: com.fasterxml.jackson.core.JsonParseException: Unrecognized token 'hello'"));
        entries.add(LogTab.parseLogLine("        ... 24 more"));
        entries.add(LogTab.parseLogLine(
                "2026-09-08 18:35:08.214  INFO 8919 --- [mqtt-source] mqtt.camel.yaml:19 : Warm temperature at 23.5"));
        return entries;
    }

    private static JsonArray lines(JsonObject data) {
        return (JsonArray) data.get("lines");
    }

    @Test
    void stackTraceLinesFoldIntoTheRecordTheyBelongTo() {
        JsonObject data = LogTab.buildLogDataJson(sampleLog(), 50, null, null);
        JsonArray rows = lines(data);

        assertEquals(3, rows.size(), "seven raw lines are three log records");
        assertEquals(7, data.get("totalLines"));
        assertEquals(3, data.get("returnedLines"));

        JsonObject newest = (JsonObject) rows.get(0);
        assertEquals("Warm temperature at 23.5", newest.get("message"));
        assertFalse(newest.containsKey("detail"));

        JsonObject warn = (JsonObject) rows.get(1);
        assertEquals("WARN", warn.get("level"));
        String detail = (String) warn.get("detail");
        assertTrue(detail.startsWith("org.apache.camel.RuntimeCamelException"), detail);
        assertTrue(detail.contains("Caused by: com.fasterxml.jackson.core.JsonParseException"), detail);
        assertTrue(detail.endsWith("... 24 more"), detail);
    }

    @Test
    void filterMatchesInsideTheFoldedDetail() {
        JsonArray rows = lines(LogTab.buildLogDataJson(sampleLog(), 50, "unrecognized token", null));

        assertEquals(1, rows.size());
        assertEquals("WARN", ((JsonObject) rows.get(0)).get("level"));
    }

    @Test
    void levelFilterAppliesToTheRecordNotToItsContinuationLines() {
        JsonArray rows = lines(LogTab.buildLogDataJson(sampleLog(), 50, null, "INFO"));

        assertEquals(2, rows.size(), "the trace lines no longer count as INFO records");
    }

    @Test
    void longTracesAreCappedWithACount() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(LogTab.parseLogLine(
                "2026-09-08 18:34:10.429  WARN 8919 --- [main] o.a.c.Foo : boom"));
        for (int i = 0; i < LogTab.MAX_DETAIL_LINES + 5; i++) {
            entries.add(LogTab.parseLogLine("        at frame" + i + "(Foo.java:" + i + ")"));
        }

        JsonObject row = (JsonObject) lines(LogTab.buildLogDataJson(entries, 50, null, null)).get(0);
        String detail = (String) row.get("detail");

        assertTrue(detail.contains("frame" + (LogTab.MAX_DETAIL_LINES - 1)), detail);
        assertFalse(detail.contains("frame" + LogTab.MAX_DETAIL_LINES + "("), detail);
        assertTrue(detail.endsWith("... 5 more lines"), detail);
    }

    @Test
    void continuationLinesWithoutAHeadStayStandalone() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(LogTab.parseLogLine("plain line without a timestamp"));
        entries.add(LogTab.parseLogLine(
                "2026-09-08 18:34:00.100  INFO 8919 --- [main] o.a.c.Foo : started"));

        JsonArray rows = lines(LogTab.buildLogDataJson(entries, 50, null, null));

        assertEquals(2, rows.size());
        assertEquals("plain line without a timestamp", ((JsonObject) rows.get(1)).get("message"));
    }
}
