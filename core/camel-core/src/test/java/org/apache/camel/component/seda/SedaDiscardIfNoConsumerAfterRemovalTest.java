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
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SedaDiscardIfNoConsumerAfterRemovalTest extends ContextTestSupport {

    @Test
    void testDiscardAfterConsumerRouteRemoved() throws Exception {
        SedaComponent seda = context.getComponent("seda", SedaComponent.class);
        SedaEndpoint bar = getMandatoryEndpoint("seda:bar?discardIfNoConsumers=true", SedaEndpoint.class);
        String key = seda.getQueueKey(bar.getEndpointUri());
        assertThat(bar.getCurrentQueueSize()).isZero();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        mock.assertIsSatisfied();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");

        assertThat(ServiceHelper.isStarted(bar)).isTrue();
        assertThat(bar.getQueueReference()).isNotNull();
        assertThat(seda.getQueues().get(key)).isNotNull();
        assertThat(bar.getQueueReference().hasConsumers()).isFalse();

        template.sendBody("direct:start", "Should be discarded");

        assertThat(bar.getCurrentQueueSize()).isZero();
    }

    @Test
    void testReAddConsumerAfterRemoval() throws Exception {
        template.sendBody("direct:start", "Hello World");
        getMockEndpoint("mock:result").assertIsSatisfied();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:bar?discardIfNoConsumers=true").routeId("consumer").to("mock:result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.reset();
        mock.expectedBodiesReceived("After re-add");

        template.sendBody("direct:start", "After re-add");

        mock.assertIsSatisfied();
    }

    @Test
    void testFailIfNoConsumersAfterConsumerRouteRemoved() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:fail").routeId("failProducer").to("seda:fail?failIfNoConsumers=true");
                from("seda:fail?failIfNoConsumers=true").routeId("failConsumer").to("mock:fail");
            }
        });

        SedaComponent seda = context.getComponent("seda", SedaComponent.class);
        SedaEndpoint fail = getMandatoryEndpoint("seda:fail?failIfNoConsumers=true", SedaEndpoint.class);
        String key = seda.getQueueKey(fail.getEndpointUri());

        context.getRouteController().stopRoute("failConsumer");
        context.removeRoute("failConsumer");

        assertThat(fail.getQueueReference()).isNotNull();
        assertThat(seda.getQueues().get(key)).isNotNull();

        assertThatThrownBy(() -> template.sendBody("direct:fail", "Should fail"))
                .cause()
                .isInstanceOf(SedaConsumerNotAvailableException.class)
                .hasMessageContaining("No consumers available");
    }

    @Test
    void testQueueRemovedAfterProducerRouteRemoved() throws Exception {
        SedaComponent seda = context.getComponent("seda", SedaComponent.class);
        SedaEndpoint bar = getMandatoryEndpoint("seda:bar?discardIfNoConsumers=true", SedaEndpoint.class);
        String key = seda.getQueueKey(bar.getEndpointUri());

        template.sendBody("direct:start", "Hello World");
        getMockEndpoint("mock:result").assertIsSatisfied();

        context.getRouteController().stopRoute("consumer");
        context.removeRoute("consumer");

        assertThat(seda.getQueues().get(key)).isNotNull();

        context.getRouteController().stopRoute("producer");
        context.removeRoute("producer");

        assertThat(seda.getQueues().get(key)).isNull();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("producer").to("seda:bar?discardIfNoConsumers=true");
                from("seda:bar?discardIfNoConsumers=true").routeId("consumer").to("mock:result");
            }
        };
    }
}
