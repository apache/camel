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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleMainTest {

    @Test
    public void testSimpleMain() throws Exception {
        List<String> events = new ArrayList<>();
        CamelContext context = new DefaultCamelContext();

        SimpleMain main = new SimpleMain(context);
        main.configure().addRoutesBuilder(new MyRouteBuilder());
        main.addMainListener(new MainListenerSupport() {
            @Override
            public void beforeInitialize(BaseMainSupport main) {
                events.add("beforeInitialize");
            }

            @Override
            public void beforeConfigure(BaseMainSupport main) {
                events.add("beforeConfigure");
            }

            @Override
            public void afterConfigure(BaseMainSupport main) {
                events.add("afterConfigure");
            }

            @Override
            public void beforeStart(BaseMainSupport main) {
                events.add("beforeStart");
            }

            @Override
            public void afterStart(BaseMainSupport main) {
                events.add("afterStart");
            }

            @Override
            public void beforeStop(BaseMainSupport main) {
                events.add("beforeStop");
            }

            @Override
            public void afterStop(BaseMainSupport main) {
                events.add("afterStop");
            }

        });
        main.start();

        try {
            assertSame(context, main.getCamelContext());

            MockEndpoint endpoint = context.getEndpoint("mock:results", MockEndpoint.class);
            endpoint.expectedMinimumMessageCount(1);

            context.createProducerTemplate().sendBody("direct:start", "<message>1</message>");

            endpoint.assertIsSatisfied();
        } finally {
            main.stop();
        }

        assertTrue(events.contains("beforeInitialize"));
        assertTrue(events.contains("beforeConfigure"));
        assertTrue(events.contains("afterConfigure"));
        assertTrue(events.contains("beforeStart"));
        assertTrue(events.contains("afterStart"));
        assertTrue(events.contains("beforeStop"));
        assertTrue(events.contains("afterStop"));
    }

    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("direct:start").to("mock:results");
        }
    }
}
