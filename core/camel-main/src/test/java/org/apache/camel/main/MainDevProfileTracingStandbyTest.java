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
package org.apache.camel.main;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Tracer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainDevProfileTracingStandbyTest {

    @Test
    public void testDevProfileEnablesTracingStandby() {
        Main main = new Main();
        main.configure().withProfile("dev");

        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertTrue(context.isTracingStandby(),
                    "dev profile should enable tracing standby");
        } finally {
            main.stop();
        }
    }

    @Test
    public void testDevProfileTracingCanBeEnabledAtRuntime() throws Exception {
        Main main = new Main();
        main.configure().withProfile("dev");
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        });

        main.start();
        try {
            CamelContext context = main.getCamelContext();
            Tracer tracer = context.getTracer();
            assertFalse(tracer.isEnabled(),
                    "tracer should be in standby (not enabled) by default in dev mode");

            tracer.setEnabled(true);
            assertTrue(tracer.isEnabled(),
                    "tracer should be enableable at runtime in dev mode");
        } finally {
            main.stop();
        }
    }

    @Test
    public void testProdProfileDoesNotEnableTracingStandby() {
        Main main = new Main();
        main.configure().withProfile("prod");

        main.start();
        try {
            CamelContext context = main.getCamelContext();
            assertFalse(context.isTracingStandby(),
                    "prod profile should not enable tracing standby");
        } finally {
            main.stop();
        }
    }

}
