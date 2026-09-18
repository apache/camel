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
package org.apache.camel.runtime.jfr;

import java.util.Map;

import jdk.jfr.Recording;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrDevConsoleTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }

    private static CamelJfrDevConsole resolveConsole(CamelContext ctx) {
        DevConsole console = PluginHelper.getDevConsoleResolver(ctx).resolveDevConsole("jfr");
        assertThat(console).as("jfr console should be resolvable").isNotNull();
        return (CamelJfrDevConsole) console;
    }

    /**
     * Enables every camel-jfr event on the recording, so a toggle has something to turn off.
     */
    private static Recording startRecordingWithAllEvents() {
        Recording recording = new Recording();
        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            recording.enable(event.getEventClass());
        }
        recording.start();
        return recording;
    }

    @Test
    void isNotReadOnly() {
        // the console can enable/disable JFR events and trigger snapshots, so it must not be advertised as safe
        assertThat(resolveConsole(context).isReadOnly()).isFalse();
    }

    @Test
    void statusReportsNotRegisteredByDefault() {
        // camel-jfr is on the classpath, but its runtime instrumentation is opt-in, so merely having the
        // recorder available must not install the hooks
        CamelJfrDevConsole console = resolveConsole(context);

        String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "status"));

        assertThat(text)
                .contains("runtimeEvents: false")
                .contains("event route")
                .contains("event processor")
                .contains("event exchange")
                .contains("event send")
                .contains("event failed")
                .contains("event redelivery");
    }

    @Test
    void statusReportsRegisteredWhenRuntimeInstrumentationEnabled() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext(false)) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRuntimeEnabled(true);
            recorder.setCamelContext(ctx);
            ctx.getCamelContextExtension().setStartupStepRecorder(recorder);
            ctx.build();
            ctx.start();

            String text = (String) resolveConsole(ctx).call(DevConsole.MediaType.TEXT, Map.of("command", "status"));

            assertThat(text).contains("runtimeEvents: true");
        }
    }

    @Test
    void enableAndDisableToggleSingleEventOnLiveRecording() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = startRecordingWithAllEvents()) {
            assertThat(CamelJfrEvents.ROUTE.isEnabled()).isTrue();

            String disabled = (String) console.call(DevConsole.MediaType.TEXT,
                    Map.of("command", "disable", "event", "route"));
            assertThat(disabled).contains("disabled route on 1 recording(s)");
            assertThat(CamelJfrEvents.ROUTE.isEnabled()).isFalse();
            assertThat(CamelJfrEvents.PROCESSOR.isEnabled()).as("only the named event may be toggled").isTrue();

            String enabled = (String) console.call(DevConsole.MediaType.TEXT,
                    Map.of("command", "enable", "event", "route"));
            assertThat(enabled).contains("enabled route on 1 recording(s)");
            assertThat(CamelJfrEvents.ROUTE.isEnabled()).isTrue();
        }
    }

    @Test
    void enableAndDisableToggleAllEventsOnLiveRecording() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = startRecordingWithAllEvents()) {
            String disabled = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "disable"));
            assertThat(disabled).contains("disabled all on 1 recording(s)");
            assertThat(CamelJfrEvents.values()).allSatisfy(e -> assertThat(e.isEnabled()).isFalse());

            String enabled = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "enable"));
            assertThat(enabled).contains("enabled all on 1 recording(s)");
            assertThat(CamelJfrEvents.values()).allSatisfy(e -> assertThat(e.isEnabled()).isTrue());
        }
    }

    @Test
    void toggleIgnoresRecordingsThatAreNotRunning() {
        CamelJfrDevConsole console = resolveConsole(context);
        // a created-but-not-started recording cannot have its events toggled, so it must not be counted
        try (Recording recording = new Recording()) {
            String result = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "enable"));

            assertThat(result).contains("no running recording");
        }
    }

    @Test
    void toggleReturnsErrorForUnknownEvent() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = startRecordingWithAllEvents()) {
            String result = (String) console.call(DevConsole.MediaType.TEXT,
                    Map.of("command", "disable", "event", "bogus"));

            assertThat(result)
                    .contains("unknown event: bogus")
                    .contains("route, processor, exchange, send, failed, redelivery, all");
        }
    }

    @Test
    void toggleReportsNoRunningRecordingWhenNoneStarted() {
        CamelJfrDevConsole console = resolveConsole(context);

        String result = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "enable"));

        assertThat(result)
                .contains("no running recording")
                .as("the message must say how to start a recording").contains("--jfr");
    }

    @Test
    void unknownCommandIsReported() {
        CamelJfrDevConsole console = resolveConsole(context);

        String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "bogus"));
        assertThat(text).contains("unknown command: bogus");

        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("command", "bogus"));
        assertThat(json.getString("error")).contains("unknown command: bogus");
    }

    @Test
    void jsonStatusAndToggleReflectLiveState() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = startRecordingWithAllEvents()) {
            JsonObject status = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("command", "status"));
            assertThat(status.getBoolean("runtimeEvents")).isFalse();
            assertThat(status.getJsonObject("events")).containsEntry("route", true);

            JsonObject toggle = (JsonObject) console.call(DevConsole.MediaType.JSON,
                    Map.of("command", "disable", "event", "route"));
            assertThat(toggle.getBoolean("success")).isTrue();
            assertThat(toggle.getString("result")).contains("disabled route on 1 recording(s)");
            assertThat(CamelJfrEvents.ROUTE.isEnabled()).isFalse();
        }
    }

    @Test
    void jsonToggleReportsFailureWhenNothingWasChanged() {
        // the caller cannot tell "nothing to do" from "done" by looking at the message alone, so the flag must say so
        CamelJfrDevConsole console = resolveConsole(context);

        JsonObject toggle = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("command", "enable"));

        assertThat(toggle.getBoolean("success")).isFalse();
        assertThat(toggle.getString("result")).contains("no running recording");
    }

    @Test
    void jfcGeneratesOverlayWithAllEventsEnabledByDefault() {
        CamelJfrDevConsole console = resolveConsole(context);

        String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "jfc"));

        assertThat(text)
                .contains("<event name=\"org.apache.camel.route\">")
                .contains("<setting name=\"enabled\">true</setting>")
                .contains("settings=default");
    }

    @Test
    void jfcUsesTheJfrEventNameNotTheJavaClassName() {
        // a .jfc overlay keyed on anything other than the @Name value silently matches no event
        CamelJfrDevConsole console = resolveConsole(context);

        String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "jfc"));

        for (CamelJfrEvents event : CamelJfrEvents.values()) {
            assertThat(text).contains("<event name=\"" + event.getEventName() + "\">");
        }
        assertThat(text).contains("<event name=\"org.apache.camel.exchange.send\">");
    }

    @Test
    void jfcHonorsDisableOptionForListedEvents() {
        CamelJfrDevConsole console = resolveConsole(context);

        // the whitespace is deliberate: a hand typed list should not have to be tightly packed
        String text = (String) console.call(DevConsole.MediaType.TEXT,
                Map.of("command", "jfc", "disable", "route, failed"));

        assertThat(text)
                .contains("<event name=\"org.apache.camel.route\">\n    <setting name=\"enabled\">false</setting>")
                .contains(
                        "<event name=\"org.apache.camel.exchange.failed\">\n    <setting name=\"enabled\">false</setting>")
                .contains(
                        "<event name=\"org.apache.camel.processor\">\n    <setting name=\"enabled\">true</setting>");
    }

    @Test
    void jfcRejectsUnknownEventInDisableList() {
        // silently ignoring a typo would hand back an overlay that does not do what was asked
        CamelJfrDevConsole console = resolveConsole(context);

        String text = (String) console.call(DevConsole.MediaType.TEXT,
                Map.of("command", "jfc", "disable", "route,bogus"));
        assertThat(text).contains("unknown event: bogus");

        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON,
                Map.of("command", "jfc", "disable", "route,bogus"));
        assertThat(json.getString("error")).contains("unknown event: bogus");
        assertThat(json.get("jfc")).isNull();
    }

    @Test
    void snapshotReturnsAggregatedDataFromActiveRecording() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext(false)) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRuntimeEnabled(true);
            recorder.setCamelContext(ctx);
            ctx.getCamelContextExtension().setStartupStepRecorder(recorder);
            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:snap").routeId("snap-route").to("mock:snap-result");
                }
            });
            ctx.build();
            ctx.start();

            try (Recording recording = startRecordingWithAllEvents()) {
                ctx.createProducerTemplate().sendBody("direct:snap", "hello");
                ctx.createProducerTemplate().sendBody("direct:snap", "world");

                CamelJfrDevConsole console = resolveConsole(ctx);
                JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("command", "snapshot"));

                assertThat(json.getBoolean("snapshot")).isTrue();
                assertThat(json.getInteger("eventCount")).isGreaterThan(0);

                JsonArray routes = json.getJsonArray("routes");
                assertThat(routes).isNotNull().isNotEmpty();

                JsonArray processors = json.getJsonArray("processors");
                assertThat(processors).isNotNull();

                JsonArray endpoints = json.getJsonArray("endpoints");
                assertThat(endpoints).isNotNull();
            }
        }
    }

    @Test
    void snapshotTextFormatContainsRouteData() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext(false)) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRuntimeEnabled(true);
            recorder.setCamelContext(ctx);
            ctx.getCamelContextExtension().setStartupStepRecorder(recorder);
            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:txt").routeId("txt-route").to("mock:txt-result");
                }
            });
            ctx.build();
            ctx.start();

            try (Recording recording = startRecordingWithAllEvents()) {
                ctx.createProducerTemplate().sendBody("direct:txt", "test");

                CamelJfrDevConsole console = resolveConsole(ctx);
                String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "snapshot"));

                assertThat(text).contains("snapshot").contains("txt-route");
            }
        }
    }

    @Test
    void snapshotWithNoRecordingReturnsErrorMessage() {
        CamelJfrDevConsole console = resolveConsole(context);

        JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("command", "snapshot"));

        assertThat(json.getBoolean("snapshot")).isTrue();
        assertThat(json.getString("error")).contains("no JFR data available");
    }
}
