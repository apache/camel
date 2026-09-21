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

/** CAMEL-24859: the reload of a written file, read from the log records newer than the ones before the write. */
class ReloadOutcomeTest {

    private static final List<String> BEFORE = List.of(
            "2026-09-21 10:00:00.001  INFO 42 --- [           main] org.apache.camel.main.MainSupport : Apache Camel 4.23.0 is starting",
            "2026-09-21 10:00:05.000  INFO 42 --- [rReloadStrategy] org.apache.camel.support.RouteWatcherReloadStrategy : Routes reloaded summary (total:1 started:1)",
            "2026-09-21 10:00:06.000  INFO 42 --- [ timer://tick] route1 : Hello Camel");

    private static final List<String> FAILED = List.of(
            "2026-09-21 10:00:20.000 ERROR 42 --- [rReloadStrategy] org.apache.camel.dsl.jbang.core.commands.ai.YamlLoadFailureReport : The route file did not load. camel validate yaml says what to write:",
            "  a.camel.yaml:",
            "    cron: the required option 'name' is missing",
            "2026-09-21 10:00:20.001  WARN 42 --- [rReloadStrategy] org.apache.camel.support.FileWatcherResourceReloadStrategy : Error reloading routes from file: a.camel.yaml due to: Error constructing YAML node id: org.apache.camel.model.FromDefinition. This exception is ignored.",
            "org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException: Error constructing YAML node id: org.apache.camel.model.FromDefinition",
            "\tat org.apache.camel.dsl.yaml.YamlRoutesBuilderLoader.doConfigure(YamlRoutesBuilderLoader.java:190)",
            "Caused by: java.lang.IllegalArgumentException: Option name is required when creating endpoint uri with syntax cron:name",
            "\tat org.apache.camel.support.component.AbstractApiEndpoint.x(Foo.java:1)");

    private static final List<String> RELOADED = List.of(
            "2026-09-21 10:00:40.000  INFO 42 --- [rReloadStrategy] org.apache.camel.support.RouteWatcherReloadStrategy : Routes reloaded summary (total:1 started:1)",
            "2026-09-21 10:00:41.000  INFO 42 --- [ timer://tick] route1 : Hello again");

    @SuppressWarnings("unchecked")
    private static List<JsonObject> records(List<String>... parts) {
        List<String> all = new java.util.ArrayList<>();
        for (List<String> p : parts) {
            all.addAll(p);
        }
        return (List<JsonObject>) (List<?>) List
                .copyOf(LogFileReader.build(all, 40, null, null, new JsonObject()).getCollection("lines"));
    }

    @Test
    void theNewestReloadBeforeTheWriteIsTheBaseline() {
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        assertEquals("10:00:05.000|Routes reloaded summary (total:1 started:1)", since);
        assertNull(ReloadOutcome.classify(records(BEFORE), since), "no reload since the write yet");
    }

    @Test
    void aFailedReloadCarriesTheCauseAndTheValidatorsReport() {
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        JsonObject out = ReloadOutcome.classify(records(BEFORE, FAILED), since);
        assertEquals("failed", out.getString("status"));
        String m = out.getString("message");
        assertTrue(m.startsWith("Error reloading routes from file: a.camel.yaml"), m);
        assertTrue(m.contains("Caused by: java.lang.IllegalArgumentException: Option name is required"), m);
        assertTrue(m.contains("camel validate yaml says what to write"), "the runtime's report is carried along: " + m);
        assertTrue(m.contains("the required option 'name' is missing"), m);
        assertTrue(!m.contains("\tat "), "no stack frames: " + m);
    }

    @Test
    void aPropertiesReloadIsReportedAsSuch() {
        List<String> props = List.of(
                "2026-09-21 10:00:30.000  INFO 42 --- [rReloadStrategy] org.apache.camel.main.DefaultConfigurationConfigurer : Reloading properties: file:application.properties");
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        JsonObject out = ReloadOutcome.classify(records(BEFORE, props), since);
        assertEquals("properties", out.getString("status"));
        assertEquals("Reloading properties: file:application.properties", out.getString("message"));
    }

    @Test
    void aReloadAfterTheFailureIsReloaded() {
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        JsonObject out = ReloadOutcome.classify(records(BEFORE, FAILED, RELOADED), since);
        assertEquals("reloaded", out.getString("status"));
        assertEquals("Routes reloaded summary (total:1 started:1)", out.getString("message"));
    }
}
