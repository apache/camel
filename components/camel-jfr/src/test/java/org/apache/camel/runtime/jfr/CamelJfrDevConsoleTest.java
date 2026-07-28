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

import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Recording;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
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

    @Test
    void statusReportsRegisteredAndEventStateByDefault() {
        // camel-jfr auto-installs CamelJfrRuntimeInstrumentation on every started context
        // via FlightRecorderStartupStepRecorder, unless runtimeEnabled is opted out.
        CamelJfrDevConsole console = resolveConsole(context);

        String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "status"));

        assertThat(text)
                .contains("registered: true")
                .contains("route")
                .contains("processor")
                .contains("exchange")
                .contains("send")
                .contains("failed")
                .contains("redelivery");
    }

    @Test
    void statusReportsNotRegisteredWhenRuntimeInstrumentationOptedOut() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext(false)) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRuntimeEnabled(false);
            recorder.setCamelContext(ctx);
            ctx.getCamelContextExtension().setStartupStepRecorder(recorder);
            ctx.build();
            ctx.start();

            CamelJfrDevConsole console = resolveConsole(ctx);
            String text = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "status"));

            assertThat(text).contains("registered: false");
        }
    }

    @Test
    void enableAndDisableToggleSingleEventOnLiveRecording() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = new Recording()) {
            for (Class<?> c : CamelJfrRuntimeInstrumentation.RUNTIME_EVENTS) {
                recording.enable(c.getName());
            }
            recording.start();
            assertThat(EventType.getEventType(CamelRouteEvent.class).isEnabled()).isTrue();

            String disabled = (String) console.call(DevConsole.MediaType.TEXT,
                    Map.of("command", "disable", "event", "route"));
            assertThat(disabled).contains("disabled route on 1 recording(s)");
            assertThat(EventType.getEventType(CamelRouteEvent.class).isEnabled()).isFalse();
            assertThat(EventType.getEventType(CamelProcessorEvent.class).isEnabled()).isTrue();

            String enabled = (String) console.call(DevConsole.MediaType.TEXT,
                    Map.of("command", "enable", "event", "route"));
            assertThat(enabled).contains("enabled route on 1 recording(s)");
            assertThat(EventType.getEventType(CamelRouteEvent.class).isEnabled()).isTrue();
        }
    }

    @Test
    void enableAndDisableToggleAllEventsOnLiveRecording() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = new Recording()) {
            for (Class<?> c : CamelJfrRuntimeInstrumentation.RUNTIME_EVENTS) {
                recording.enable(c.getName());
            }
            recording.start();

            String disabled = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "disable"));
            assertThat(disabled).contains("disabled all on 1 recording(s)");
            for (Class<?> c : CamelJfrRuntimeInstrumentation.RUNTIME_EVENTS) {
                assertThat(EventType.getEventType(c.asSubclass(Event.class)).isEnabled()).isFalse();
            }

            String enabled = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "enable"));
            assertThat(enabled).contains("enabled all on 1 recording(s)");
            for (Class<?> c : CamelJfrRuntimeInstrumentation.RUNTIME_EVENTS) {
                assertThat(EventType.getEventType(c.asSubclass(Event.class)).isEnabled()).isTrue();
            }
        }
    }

    @Test
    void toggleReturnsErrorForUnknownEvent() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = new Recording()) {
            recording.start();

            String result = (String) console.call(DevConsole.MediaType.TEXT,
                    Map.of("command", "disable", "event", "bogus"));

            assertThat(result)
                    .contains("unknown event: bogus")
                    .contains("route, processor, exchange, send, failed, redelivery, all");
        }
    }

    @Test
    void toggleReportsNoActiveRecordingWhenNoneRunning() {
        CamelJfrDevConsole console = resolveConsole(context);

        String result = (String) console.call(DevConsole.MediaType.TEXT, Map.of("command", "enable"));

        assertThat(result).contains("no active recording");
    }

    @Test
    void jsonStatusAndToggleReflectLiveState() {
        CamelJfrDevConsole console = resolveConsole(context);
        try (Recording recording = new Recording()) {
            recording.enable(CamelRouteEvent.class.getName());
            recording.start();

            JsonObject status = (JsonObject) console.call(DevConsole.MediaType.JSON, Map.of("command", "status"));
            assertThat(status.getBoolean("registered")).isTrue();

            JsonObject toggle = (JsonObject) console.call(DevConsole.MediaType.JSON,
                    Map.of("command", "disable", "event", "route"));
            assertThat(toggle.getString("result")).contains("disabled route on 1 recording(s)");
            assertThat(EventType.getEventType(CamelRouteEvent.class).isEnabled()).isFalse();
        }
    }
}
