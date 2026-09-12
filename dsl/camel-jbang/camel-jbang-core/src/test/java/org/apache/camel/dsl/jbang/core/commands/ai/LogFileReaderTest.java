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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileReaderTest {

    private static final List<String> LINES = List.of(
            "2026-09-12 10:00:00.001  INFO 42 --- [           main] org.apache.camel.main.MainSupport : Apache Camel 4.23.0 is starting",
            "2026-09-12 10:00:01.002  WARN 42 --- [           main] o.a.c.impl.engine.AbstractCamelContext : Routes startup (started:1)",
            "2026-09-12 10:00:02.003 ERROR 42 --- [ timer://tick] org.apache.camel.processor.errorhandler.DefaultErrorHandler : Failed delivery",
            "java.lang.IllegalStateException: boom",
            "\tat org.example.Foo.bar(Foo.java:12)",
            "\tat org.example.Foo.baz(Foo.java:34)",
            "2026-09-12 10:00:03.004  INFO 42 --- [ timer://tick] route1 : Hello Camel");

    @Test
    void groupsContinuationLinesIntoTheRecordBeforeThemNewestFirst() {
        JsonObject result = LogFileReader.build(LINES, 50, null, null, new JsonObject());
        assertEquals(7, result.getInteger("totalLines"));
        assertEquals(4, result.getInteger("returnedLines"));
        List<JsonObject> rows = List.copyOf(result.getCollection("lines"));
        assertEquals("Hello Camel", rows.get(0).getString("message"));
        assertEquals("route1", rows.get(0).getString("logger"));
        assertEquals("10:00:03.004", rows.get(0).getString("time"));
        JsonObject error = rows.get(1);
        assertEquals("ERROR", error.getString("level"));
        assertEquals("DefaultErrorHandler", error.getString("logger"), "the logger is shortened to its class");
        assertTrue(error.getString("detail").startsWith("java.lang.IllegalStateException: boom\n\tat"));
        assertNull(rows.get(0).get("detail"));
    }

    @Test
    void filtersByLevelAndTextAndHonoursTheLimit() {
        JsonObject errors = LogFileReader.build(LINES, 50, null, "error", new JsonObject());
        assertEquals(1, errors.getInteger("returnedLines"));
        JsonObject boom = LogFileReader.build(LINES, 50, "BOOM", null, new JsonObject());
        assertEquals(1, boom.getInteger("returnedLines"), "the filter also matches the detail block");
        assertEquals("Failed delivery", ((JsonObject) boom.getCollection("lines").iterator().next())
                .getString("message"));
        assertEquals(2, LogFileReader.build(LINES, 2, null, null, new JsonObject()).getInteger("returnedLines"));
        assertEquals(0, LogFileReader.build(List.of(), 2, null, null, new JsonObject()).getInteger("returnedLines"));
    }
}
