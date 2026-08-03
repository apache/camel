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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.jfr.consumer.RecordedEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamelJfrBootstrapTest extends JfrRecordingTestSupport {

    private static final Set<String> RUNTIME_NAMES = Arrays.stream(CamelJfrEvents.values())
            .map(CamelJfrEvents::getEventName)
            .collect(Collectors.toUnmodifiableSet());

    @Test
    void runtimeEnabledRecorderEmitsRuntimeEvents() throws Exception {
        List<RecordedEvent> events = recordAndRun(() -> runRoute(true));
        assertThat(events).extracting(e -> e.getEventType().getName()).anyMatch(RUNTIME_NAMES::contains);
    }

    @Test
    void runtimeInstrumentationIsOffByDefault() throws Exception {
        // the recorder is on the classpath by default, so leaving runtimeEnabled alone must keep the
        // 4.21 behaviour of recording startup steps only
        List<RecordedEvent> events = recordAndRun(() -> {
            try (DefaultCamelContext context = new DefaultCamelContext(false)) {
                FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
                assertThat(recorder.isRuntimeEnabled()).isFalse();
                recorder.setCamelContext(context);
                context.getCamelContextExtension().setStartupStepRecorder(recorder);
                context.build();
                context.addRoutes(routes());
                context.start();
                context.createProducerTemplate().sendBody("direct:start", "hi");
            }
        });
        assertThat(events).extracting(e -> e.getEventType().getName()).noneMatch(RUNTIME_NAMES::contains);
    }

    @Test
    void optOutEmitsNoRuntimeEvents() throws Exception {
        List<RecordedEvent> events = recordAndRun(() -> runRoute(false));
        assertThat(events).extracting(e -> e.getEventType().getName()).noneMatch(RUNTIME_NAMES::contains);
    }

    private void runRoute(boolean runtimeEnabled) throws Exception {
        // build=false: a fully built DefaultCamelContext() already auto-discovers and starts
        // the classpath FlightRecorderStartupStepRecorder in its constructor, which is too late
        // to override runtimeEnabled. Deferring build() lets us install our own recorder first.
        try (DefaultCamelContext context = new DefaultCamelContext(false)) {
            FlightRecorderStartupStepRecorder recorder = new FlightRecorderStartupStepRecorder();
            recorder.setRuntimeEnabled(runtimeEnabled);
            // classpath auto-discovery injects the CamelContext via DefaultInjector; replicate that here
            recorder.setCamelContext(context);
            context.getCamelContextExtension().setStartupStepRecorder(recorder);
            context.build();
            context.addRoutes(routes());
            context.start();
            context.createProducerTemplate().sendBody("direct:start", "hi");
        }
    }

    private static RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("main").to("mock:out");
            }
        };
    }
}
