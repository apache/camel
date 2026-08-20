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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SedaDiscardIfNoConsumerAfterRemovalTest extends ContextTestSupport {

    @Test
    void testDiscardAfterConsumerRouteRemoved() throws Exception {
        SedaEndpoint bar = getMandatoryEndpoint("seda:bar?discardIfNoConsumers=true", SedaEndpoint.class);
        assertThat(bar.getCurrentQueueSize()).isZero();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");

        mock.reset();
        mock.expectedMessageCount(0);

        template.sendBody("direct:start", "Should be discarded");

        mock.assertIsSatisfied();
        assertThat(bar.getCurrentQueueSize()).isZero();
    }

    @Test
    void testQueueReferenceKeptWhenProducerStillActive() throws Exception {
        SedaComponent seda = context.getComponent("seda", SedaComponent.class);
        SedaEndpoint bar = getMandatoryEndpoint("seda:bar?discardIfNoConsumers=true", SedaEndpoint.class);
        String key = seda.getQueueKey(bar.getEndpointUri());

        template.sendBody("direct:start", "Hello World");
        getMockEndpoint("mock:result").assertIsSatisfied();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");

        assertThat(seda.getQueues().get(key)).isNotNull();
        assertThat(seda.getQueues().get(key).getCount()).isGreaterThan(0);
    }

    @Test
    void testFailIfNoConsumersAfterConsumerRouteRemoved() throws Exception {
        context.getRouteController().stopRoute("producer");
        context.removeRoute("producer");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fail").to("seda:fail?failIfNoConsumers=true");
            }
        });

        context.getRouteController().stopRoute("failConsumer");
        context.removeRoute("failConsumer");

        assertThatThrownBy(() -> template.sendBody("direct:fail", "Should fail"))
                .hasCauseInstanceOf(SedaConsumerNotAvailableException.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("producer").to("seda:bar?discardIfNoConsumers=true");
                from("seda:bar?discardIfNoConsumers=true").routeId("consumer").to("mock:result");
                from("seda:fail").routeId("failConsumer").to("mock:fail");
            }
        };
    }
}
