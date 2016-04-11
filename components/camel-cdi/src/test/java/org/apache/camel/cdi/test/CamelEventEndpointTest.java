/**
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
package org.apache.camel.cdi.test;

import java.util.EventObject;
import javax.inject.Inject;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.CdiEventEndpoint;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.either;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class CamelEventEndpointTest {

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test class
            .addClass(CamelEventRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void camelStartedEvent(@Uri("mock:started") MockEndpoint started) {
        assertThat("Event fired is incorrect!", started.getExchanges(),
            contains(
                hasProperty("in",
                    hasProperty("body", instanceOf(CamelContextStartedEvent.class)))));
    }

    @Test
    public void camelAllEvents(@Uri("mock:events") MockEndpoint events) {
        assertThat("Events fired are incorrect!", events.getExchanges(),
            // We cannot rely on the delivery order of the camel context started event being fired and observed by both CDI event endpoints
            either(
                contains(
                    // Started route: route1
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(RouteStartedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class))),
                    // Started route: route2
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(RouteStartedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class))),
                    // Started CamelContext: camel-cdi
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(CamelContextStartedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class))),
                    // Started CamelContext: camel-cdi (for CdiEventEndpoint<CamelContextStartedEvent> started)
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class)))
            )).or(
                contains(
                    // Started route: route1
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(RouteStartedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class))),
                    // Started route: route2
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(RouteStartedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class))),
                    // Started CamelContext: camel-cdi (for CdiEventEndpoint<CamelContextStartedEvent> started)
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class))),
                    // Started CamelContext: camel-cdi
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCreatedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSendingEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(CamelContextStartedEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeSentEvent.class))),
                    hasProperty("in", hasProperty("body", instanceOf(ExchangeCompletedEvent.class)))
                )
            )
        );
    }
}

class CamelEventRoute extends RouteBuilder {

    @Inject
    private CdiEventEndpoint<CamelContextStartedEvent> started;

    @Inject
    private CdiEventEndpoint<EventObject> events;

    @Override
    public void configure() {
        from(events).startupOrder(1).to("mock:events");
        from(started).startupOrder(2).to("mock:started");
    }
}
