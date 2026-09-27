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
package org.apache.camel.component.ai.tool;

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
 * Tools must be registered before any route consumer starts, so a route declared before the ai-tool routes (such as
 * {@code stream:in}) that sends a request immediately on start sees all of them.
 */
class AiToolStartupOrderTest extends CamelTestSupport {

    private final List<String> seenByFirstConsumer = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // declared first: its consumer starts before the ai-tool consumers
                from(new ProbeEndpoint(getContext())).routeId("probe").log("probe");

                from("ai-tool:no_params?tags=devops&description=No parameters")
                        .setBody(constant("ok"));

                from("ai-tool:with_params?tags=devops&description=With parameters"
                     + "&parameter.service=string&parameter.service.description=The service")
                        .setBody(constant("ok"));

                from("ai-tool:not_started?tags=devops&description=Route not auto started")
                        .autoStartup(false)
                        .setBody(constant("ok"));
            }
        };
    }

    @Test
    void toolsAreRegisteredBeforeTheFirstRouteConsumerStarts() {
        assertThat(seenByFirstConsumer).containsExactlyInAnyOrder("no_params", "with_params");
    }

    @Test
    void toolOfRouteNotAutoStartedIsNotRegistered() {
        assertThat(AiToolRegistry.getOrCreate(context).getToolsByTag("devops"))
                .extracting(AiToolSpec::getName)
                .containsExactlyInAnyOrder("no_params", "with_params");
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
                    AiToolRegistry.getOrCreate(getCamelContext()).getToolsByTag("devops")
                            .forEach(spec -> seenByFirstConsumer.add(spec.getName()));
                }
            };
        }
    }
}
