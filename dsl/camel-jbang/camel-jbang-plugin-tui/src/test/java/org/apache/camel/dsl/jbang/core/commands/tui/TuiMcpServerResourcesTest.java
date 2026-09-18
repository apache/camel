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

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TuiMcpServerResourcesTest {

    @Test
    void parsesWholeDocumentAndSectionUris() {
        TuiMcpServer.StatusUri whole = TuiMcpServer.StatusUri.parse("camel://status/4711");
        assertEquals("4711", whole.pid());
        assertNull(whole.section());
        assertEquals("camel://status/4711", whole.uri());

        TuiMcpServer.StatusUri section = TuiMcpServer.StatusUri.parse("camel://status/4711/main-configuration");
        assertEquals("4711", section.pid());
        assertEquals("main-configuration", section.section());
        assertEquals("camel://status/4711/main-configuration", section.uri());
    }

    @Test
    void rejectsUrisThatAreNotStatusResources() {
        assertNull(TuiMcpServer.StatusUri.parse(null));
        assertNull(TuiMcpServer.StatusUri.parse("file:///etc/passwd"));
        assertNull(TuiMcpServer.StatusUri.parse("camel://status/"));
        assertNull(TuiMcpServer.StatusUri.parse("camel://status/abc"));
        assertNull(TuiMcpServer.StatusUri.parse("camel://status/4711/"));
        assertNull(TuiMcpServer.StatusUri.parse("camel://status/4711/a/b"));
        assertNull(TuiMcpServer.StatusUri.parse("camel://status/../4711"));
    }

    @Test
    void parsesLogUrisWithOptionalLineCount() {
        TuiMcpServer.LogUri plain = TuiMcpServer.LogUri.parse("camel://log/4711");
        assertEquals("4711", plain.pid());
        assertEquals(StatusFileReader.DEFAULT_LOG_LINES, plain.lines());
        assertEquals("camel://log/4711", plain.uri());

        TuiMcpServer.LogUri sized = TuiMcpServer.LogUri.parse("camel://log/4711?lines=50");
        assertEquals(50, sized.lines());
        assertEquals("camel://log/4711?lines=50", sized.uri());
        assertEquals(StatusFileReader.MAX_LOG_LINES, TuiMcpServer.LogUri.parse("camel://log/4711?lines=999999").lines());

        assertNull(TuiMcpServer.LogUri.parse("camel://log/"));
        assertNull(TuiMcpServer.LogUri.parse("camel://log/abc"));
        assertNull(TuiMcpServer.LogUri.parse("camel://log/4711?lines=0"));
        assertNull(TuiMcpServer.LogUri.parse("camel://log/4711?tail=5"));
        assertNull(TuiMcpServer.LogUri.parse("camel://status/4711"));
    }

    @Test
    void listsLogThenWholeDocumentThenEachSectionPerIntegration(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("4711-status.json"),
                "{\"runtime\":{\"pid\":4711},\"context\":{\"name\":\"timer-log\"}}");
        Files.writeString(dir.resolve("4711.log"), "hello\n");
        IntegrationInfo running = new IntegrationInfo();
        running.pid = "4711";
        running.name = "timer-log";
        IntegrationInfo gone = new IntegrationInfo();
        gone.pid = "4712";
        gone.name = "no-file";

        JsonArray resources = TuiMcpServer.buildResourceList(List.of(running, gone), new StatusFileReader(dir));

        assertEquals(4, resources.size());
        JsonObject log = (JsonObject) resources.get(0);
        assertEquals("camel://log/4711", log.get("uri"));
        assertEquals("timer-log log", log.get("name"));
        assertEquals("text/plain", log.get("mimeType"));
        JsonObject whole = (JsonObject) resources.get(1);
        assertEquals("camel://status/4711", whole.get("uri"));
        assertEquals("timer-log status", whole.get("name"));
        assertEquals("application/json", whole.get("mimeType"));
        assertEquals("camel://status/4711/runtime", ((JsonObject) resources.get(2)).get("uri"));
        assertEquals("camel://status/4711/context", ((JsonObject) resources.get(3)).get("uri"));
    }
}
