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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.util.json.JsonArray;
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

    private static final List<String> PROPERTIES = List.of(
            "2026-09-21 10:00:30.000  INFO 42 --- [rReloadStrategy] org.apache.camel.main.DefaultConfigurationConfigurer : Reloading properties: file:application.properties");

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
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        JsonObject out = ReloadOutcome.classify(records(BEFORE, PROPERTIES), since);
        assertEquals("properties", out.getString("status"));
        assertEquals("Reloading properties: file:application.properties", out.getString("message"));
    }

    /** CAMEL-25661: the routes are reloaded after the properties they use, and their outcome is the answer. */
    @Test
    void aPropertiesReloadIsAnsweredWithTheRoutesReloadAfterIt() {
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        List<List<JsonObject>> polls
                = List.of(records(BEFORE), records(BEFORE, PROPERTIES), records(BEFORE, PROPERTIES, RELOADED));
        AtomicInteger poll = new AtomicInteger();
        JsonObject out = ReloadOutcome.await(() -> polls.get(Math.min(poll.getAndIncrement(), polls.size() - 1)), since,
                5000);
        assertEquals("reloaded", out.getString("status"));
        assertEquals("Routes reloaded summary (total:1 started:1)", out.getString("message"));
    }

    @Test
    void aPropertiesReloadWithoutARoutesReloadAfterItIsReportedAsSuch() {
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        JsonObject out = ReloadOutcome.await(() -> records(BEFORE, PROPERTIES), since, 5000);
        assertEquals("properties", out.getString("status"));
    }

    @Test
    void aReloadAfterTheFailureIsReloaded() {
        String since = ReloadOutcome.latestReloadKey(records(BEFORE));
        JsonObject out = ReloadOutcome.classify(records(BEFORE, FAILED, RELOADED), since);
        assertEquals("reloaded", out.getString("status"));
        assertEquals("Routes reloaded summary (total:1 started:1)", out.getString("message"));
    }

    /** CAMEL-25513: what the routes logged after the reload, the result of the write. */
    @Test
    void theOutputIsWhatTheRoutesLoggedAfterTheReload() {
        List<String> xslt = List.of(
                "2026-10-10 04:44:26.089  INFO 22543 --- [rReloadStrategy] e.camel.component.file.FileEndpoint : Endpoint is configured with noop=true so forcing endpoint to be idempotent as well",
                "2026-10-10 04:44:26.097  INFO 22543 --- [rReloadStrategy] org.apache.camel.support.RouteWatcherReloadStrategy : Routes reloaded summary (total:1 started:1)",
                "2026-10-10 04:44:26.097  INFO 22543 --- [rReloadStrategy] org.apache.camel.support.RouteWatcherReloadStrategy :     Started xslt (file://inbox) (source: xslt.camel.yaml:4)",
                "2026-10-10 04:44:27.167  INFO 22543 --- [ - file://inbox] xslt.camel.yaml:12                  : Packing slip: <?xml version=\"1.0\" encoding=\"UTF-8\"?><packingSlip orderId=\"\" customer=\"C-482\" country=\"\"/>",
                "2026-10-10 04:44:28.001  WARN 22543 --- [ - file://inbox] org.apache.camel.component.file.GenericFileOnCompletion : Rollback file strategy: x");

        JsonArray output = ReloadOutcome.output(records(BEFORE, xslt));

        assertEquals(2, output.size(), output.toJson());
        assertEquals("Packing slip: <?xml version=\"1.0\" encoding=\"UTF-8\"?><packingSlip orderId=\"\" customer=\"C-482\""
                     + " country=\"\"/>",
                output.get(0));
        assertTrue(output.get(1).toString().startsWith("WARN Rollback file strategy"), output.toJson());
    }

    /** The errors after the first lines are the news: a route that went on and failed later on. */
    @Test
    void anErrorAfterTheFirstLinesIsKept() {
        List<String> lines = new java.util.ArrayList<>(RELOADED);
        for (int i = 1; i <= 6; i++) {
            lines.add("2026-09-21 10:00:4" + (i + 1) + ".000  INFO 42 --- [ timer://tick] route1 : Order " + i);
        }
        lines.add("2026-09-21 10:00:48.500 ERROR 42 --- [ timer://tick] o.a.c.p.e.DefaultErrorHandler : Failed delivery for"
                  + " exchangeId: A. Exhausted after delivery attempt: 1 caught: java.lang.IllegalStateException: stock");
        lines.add("2026-09-21 10:00:49.000  INFO 42 --- [ timer://tick] route1 : Order 7");

        JsonArray output = ReloadOutcome.output(records(BEFORE, lines));

        assertEquals(List.of("Hello again", "Order 1", "Order 2", "Order 3", "Order 4"), List.copyOf(output.subList(0, 5)));
        assertTrue(output.get(5).toString().startsWith("ERROR Failed delivery for exchangeId: A"), output.toJson());
        assertEquals("... 3 more lines: camel_get_log shows them", output.get(6));
        assertEquals(7, output.size(), output.toJson());
    }

    /**
     * The line of a failed exchange starts with the message ids and the attempts of the error handler, and the hint of
     * the failure comes late in the exception message: cut at 400 characters, the agent never saw it (enrich step 2 of
     * the r20 runs). The line is compacted, and the hint fits.
     */
    @Test
    void theHintOfAFailedExchangeFitsTheLine() {
        List<String> failed = List.of(
                "2026-10-21 00:30:00.000  INFO 42 --- [rReloadStrategy] org.apache.camel.support.RouteWatcherReloadStrategy : Routes reloaded summary (total:1 started:1)",
                "2026-10-21 00:30:01.000 ERROR 42 --- [ - file://orders] or.errorhandler.DefaultErrorHandler : Failed delivery for"
                                                                                                                                                                            + " (MessageId: 7188B2FCF80AE47-000000000000001B on ExchangeId: 7188B2FCF80AE47-000000000000001B) at"
                                                                                                                                                                            + " incoming-orders[log14] enrich-and-dedupe.camel.yaml:42. Exhausted after delivery attempt: 1 caught:"
                                                                                                                                                                            + " org.apache.camel.language.bean.RuntimeBeanExpressionException: Failed to invoke method: [name] on"
                                                                                                                                                                            + " org.apache.camel.component.file.GenericFile due to: java.lang.IndexOutOfBoundsException: Key: name"
                                                                                                                                                                            + " not found in bean: GenericFile[C-482.json] of type: org.apache.camel.component.file.GenericFile"
                                                                                                                                                                            + " using OGNL path [[name]] (the value is the file as read (a GenericFile), not its data: unmarshal it"
                                                                                                                                                                            + " first, for example with json, then read the key; a variable is unmarshalled in place with"
                                                                                                                                                                            + " variableSend and variableReceive set to its name, such as unmarshal: {json: {}, variableSend:"
                                                                                                                                                                            + " customer, variableReceive: customer})");

        JsonArray output = ReloadOutcome.output(records(BEFORE, failed));

        assertEquals(1, output.size(), output.toJson());
        String line = output.get(0).toString();
        assertTrue(line.startsWith("ERROR Failed at incoming-orders[log14] enrich-and-dedupe.camel.yaml:42:"
                                   + " RuntimeBeanExpressionException: Failed to invoke method: [name]"),
                line);
        // the whole hint, not cut
        assertTrue(line.endsWith("variableSend: customer, variableReceive: customer})"), line);
        assertTrue(!line.contains("MessageId") && !line.contains("Exhausted after"), line);
    }

    @Test
    void theDumpOfAnExchangeAndOfALongBeanGo() {
        String m = ReloadOutcome.compact("Method with name: orderId not found on bean: ["
                                         + "{orderId=ORD-1001, customer=C-482}, ".repeat(5)
                                         + "] of type: java.util.ArrayList on the exchange: Exchange[ABC-1]");
        assertTrue(m.startsWith("Method with name: orderId not found on bean: [{orderId=ORD-1001, customer=C-482}"), m);
        assertTrue(m.endsWith("... of type: java.util.ArrayList"), m);
        assertEquals("Error processing exchange. Caused by: [NullPointerException - Cannot get property 'id' on null object]",
                ReloadOutcome.compact("Error processing exchange. Exchange[1028039098EFE4D-0000000000000009]. Caused by:"
                                      + " [java.lang.NullPointerException - Cannot get property 'id' on null object]"));
        // a short dump stays
        assertEquals("Key: name not found in bean: GenericFile[C-482.json] of type: x",
                ReloadOutcome.compact("Key: name not found in bean: GenericFile[C-482.json] of type: x"));
    }

    @Test
    void whatWasLoggedBeforeTheNewestReloadIsNotOutput() {
        assertEquals(List.of("Hello Camel"), List.copyOf(ReloadOutcome.output(records(BEFORE))));
        // after the next reload "Hello Camel" is the result of the write before
        assertEquals(List.of("Hello again"), List.copyOf(ReloadOutcome.output(records(BEFORE, RELOADED))));
    }
}
