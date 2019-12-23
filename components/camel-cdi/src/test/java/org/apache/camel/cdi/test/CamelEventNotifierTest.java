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
package org.apache.camel.cdi.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.SimpleCamelRoute;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStartingEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStoppedEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStoppingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCompletedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeCreatedEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSendingEvent;
import org.apache.camel.spi.CamelEvent.ExchangeSentEvent;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class CamelEventNotifierTest {

    @Inject
    @Uri("direct:start")
    private ProducerTemplate inbound;

    @Inject
    @Uri("mock:result")
    private MockEndpoint outbound;

    @Produces
    @ApplicationScoped
    private List<Class> firedEvents = new ArrayList<>();

    private void onCamelContextStartingEvent(@Observes CamelContextStartingEvent event, List<Class> events) {
        events.add(CamelContextStartingEvent.class);
    }

    private void onCamelContextStartedEvent(@Observes CamelContextStartedEvent event, List<Class> events) {
        events.add(CamelContextStartedEvent.class);
    }

    private void onExchangeEvent(@Observes ExchangeEvent event, List<Class> events) {
        events.add(event.getClass().getInterfaces()[0]);
    }

    private void onCamelContextStoppingEvent(@Observes CamelContextStoppingEvent event, List<Class> events) {
        events.add(CamelContextStoppingEvent.class);
    }

    private void onCamelContextStoppedEvent(@Observes CamelContextStoppedEvent event, List<Class> events) {
        events.add(CamelContextStoppedEvent.class);
    }

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test class
            .addClass(SimpleCamelRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    @InSequence(1)
    public void startedCamelContext(List<Class> events) {
        assertThat("Events fired are incorrect!", events,
            contains(
                CamelContextStartingEvent.class,
                CamelContextStartedEvent.class));
    }

    @Test
    @InSequence(2)
    public void sendMessageToInbound(List<Class> events) throws InterruptedException {
        outbound.expectedMessageCount(1);
        outbound.expectedBodiesReceived("test");

        inbound.sendBody("test");

        assertIsSatisfied(2L, TimeUnit.SECONDS, outbound);

        assertThat("Events fired are incorrect!", events,
            contains(
                CamelContextStartingEvent.class,
                CamelContextStartedEvent.class,
                ExchangeSendingEvent.class,
                ExchangeCreatedEvent.class,
                ExchangeSendingEvent.class,
                ExchangeSentEvent.class,
                ExchangeCompletedEvent.class,
                ExchangeSentEvent.class));
    }

    @Test
    @InSequence(3)
    public void stopCamelContext(CamelContext context, List<Class> events) throws Exception {
        context.stop();

        assertThat("Events fired are incorrect!", events,
            contains(
                CamelContextStartingEvent.class,
                CamelContextStartedEvent.class,
                ExchangeSendingEvent.class,
                ExchangeCreatedEvent.class,
                ExchangeSendingEvent.class,
                ExchangeSentEvent.class,
                ExchangeCompletedEvent.class,
                ExchangeSentEvent.class,
                CamelContextStoppingEvent.class,
                CamelContextStoppedEvent.class));
    }
}
