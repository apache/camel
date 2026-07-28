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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.startup.jfr.FlightRecorderStartupStepRecorder;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
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
}
