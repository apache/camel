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
package org.apache.camel.component.ai.prompt;

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

/** Prompt routes must register during warm-up before other route consumers start. */
class AiPromptStartupOrderTest extends CamelTestSupport {

    private final List<String> seenByFirstConsumer = new CopyOnWriteArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(new ProbeEndpoint(getContext())).routeId("probe").log("probe");

                from("ai-prompt:app_prompt?tags=crm")
                        .setBody(constant("Application prompt"));

                from("ai-prompt:not_started?tags=crm")
                        .autoStartup(false)
                        .setBody(constant("Not registered yet"));
            }
        };
    }

    @Test
    void promptsAreRegisteredBeforeTheFirstRouteConsumerStarts() {
        assertThat(seenByFirstConsumer).containsExactly("app_prompt");
    }

    @Test
    void promptOfRouteNotAutoStartedIsNotRegistered() {
        assertThat(AiPromptRegistry.getOrCreate(context).getPrompts().get("crm"))
                .extracting(AiPromptSpec::getName)
                .containsExactly("app_prompt");
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
                    AiPromptRegistry.getOrCreate(getCamelContext()).getPrompts().get("crm")
                            .forEach(spec -> seenByFirstConsumer.add(spec.getName()));
                }
            };
        }
    }
}
