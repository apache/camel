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
package org.apache.camel.component.ai.resource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resources must be registered before any route consumer starts, so a route declared before the ai-resource routes sees
 * all of them.
 */
class AiResourceStartupOrderTest extends CamelTestSupport {

    private final List<String> seenByFirstConsumer = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // declared first: its consumer starts before the ai-resource consumers
                from(new ProbeEndpoint(getContext())).routeId("probe").log("probe");

                from("ai-resource:app_config?resourceUri=camel:///config/app.json&tags=crm")
                        .setBody(constant("{}"));

                from("ai-resource:not_started?resourceUri=camel:///config/other.json&tags=crm")
                        .autoStartup(false)
                        .setBody(constant("{}"));
            }
        };
    }

    @Test
    void resourcesAreRegisteredBeforeTheFirstRouteConsumerStarts() {
        assertThat(seenByFirstConsumer).containsExactly("camel:///config/app.json");
    }

    @Test
    void resourceOfRouteNotAutoStartedIsNotRegistered() {
        assertThat(AiResourceRegistry.getOrCreate(context).getResourcesByTag("crm"))
                .extracting(AiResourceSpec::getUri)
                .containsExactly("camel:///config/app.json");
    }

    private final class ProbeEndpoint extends DefaultEndpoint {

        ProbeEndpoint(CamelContext camelContext) {
            super("probe://first", null);
            setCamelContext(camelContext);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
                @Override
                protected void doStart() throws Exception {
                    super.doStart();
                    AiResourceRegistry.getOrCreate(getCamelContext()).getResourcesByTag("crm")
                            .forEach(spec -> seenByFirstConsumer.add(spec.getUri()));
                }
            };
        }
    }
}
