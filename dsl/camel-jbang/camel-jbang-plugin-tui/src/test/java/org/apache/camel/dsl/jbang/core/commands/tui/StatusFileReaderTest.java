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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusFileReaderTest {

    private static final String STATUS = """
            {"runtime":{"pid":4711,"directory":"/tmp/app"},
             "context":{"name":"timer-log","uptime":262307,"startTimestamp":1788879661524},
             "routes":[{"routeId":"route1","state":"Started"}]}
            """;

    @Test
    void readsSectionsInFileOrder(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("4711-status.json"), STATUS);
        StatusFileReader reader = new StatusFileReader(dir);

        assertEquals(List.of("runtime", "context", "routes"), reader.sections("4711"));
    }

    @Test
    void returnsOneSectionOrNull(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("4711-status.json"), STATUS);
        StatusFileReader reader = new StatusFileReader(dir);

        JsonObject context = (JsonObject) reader.section("4711", "context");
        assertEquals("timer-log", context.get("name"));
        assertEquals(262307L, ((Number) context.get("uptime")).longValue());
        assertNull(reader.section("4711", "nope"));
        assertNull(reader.section("4711", null));
    }

    @Test
    void missingOrCorruptFilesReadAsAbsent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("99-status.json"), "{ not json");
        StatusFileReader reader = new StatusFileReader(dir);

        assertNull(reader.read("4711"));
        assertNull(reader.read("99"));
        assertNull(reader.read(" "));
        assertTrue(reader.sections("4711").isEmpty());
    }
}
