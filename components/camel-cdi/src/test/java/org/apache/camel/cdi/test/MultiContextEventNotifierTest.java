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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.DefaultCamelContextBean;
import org.apache.camel.cdi.bean.FirstCamelContextBean;
import org.apache.camel.cdi.bean.FirstCamelContextRoute;
import org.apache.camel.cdi.bean.SecondCamelContextBean;
import org.apache.camel.cdi.bean.UriEndpointRoute;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent.CamelContextStartedEvent;
import org.apache.camel.spi.CamelEvent.CamelContextStartingEvent;
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

import static org.apache.camel.cdi.expression.ExchangeExpression.fromCamelContext;
import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isOneOf;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class MultiContextEventNotifierTest {

    @Inject
    // Support bean class injection for custom beans
    private DefaultCamelContextBean defaultCamelContext;

    @Inject @Uri("direct:inbound")
    private ProducerTemplate defaultInbound;

    @Inject @Uri("mock:outbound")
    private MockEndpoint defaultOutbound;

    @Produces @ApplicationScoped @Named("defaultContext")
    private List<Class> defaultFiredEvents = new ArrayList<>();


    @Inject @ContextName("first")
    private CamelContext firstCamelContext;

    @Inject @ContextName("first") @Uri("direct:inbound")
    private ProducerTemplate firstInbound;

    @Inject @ContextName("first") @Uri("mock:outbound")
    private MockEndpoint firstOutbound;

    @Produces @ApplicationScoped @ContextName("first")
    private List<Class> firstFiredEvents = new ArrayList<>();


    @Inject @ContextName("second")
    private CamelContext secondCamelContext;

    @Inject @ContextName("second") @Uri("direct:inbound")
    private ProducerTemplate secondInbound;

    @Inject @ContextName("second") @Uri("mock:outbound")
    private MockEndpoint secondOutbound;

    @Produces @ApplicationScoped @ContextName("second")
    private List<Class> secondFiredEvents = new ArrayList<>();


    @Produces @ApplicationScoped @Named("anyContext")
    private List<Class> anyFiredEvents = new ArrayList<>();


    private void onAnyContextStartingEvent(@Observes CamelContextStartingEvent event, @Named("anyContext") List<Class> events) {
        events.add(CamelContextStartingEvent.class);
    }

    private void onAnyContextStartedEvent(@Observes CamelContextStartedEvent event, @Named("anyContext") List<Class> events) {
        events.add(CamelContextStartedEvent.class);
    }

    private void onAnyExchangeEvent(@Observes ExchangeEvent event, @Named("anyContext") List<Class> events) {
        events.add(event.getClass().getInterfaces()[0]);
    }


    private void onDefaultContextStartingEvent(@Observes @Default CamelContextStartingEvent event, @Named("defaultContext") List<Class> events) {
        events.add(CamelContextStartingEvent.class);
    }

    private void onDefaultContextStartedEvent(@Observes @Default CamelContextStartedEvent event, @Named("defaultContext") List<Class> events) {
        events.add(CamelContextStartedEvent.class);
    }

    private void onDefaultExchangeEvent(@Observes @Default ExchangeEvent event, @Named("defaultContext") List<Class> events) {
        events.add(event.getClass().getInterfaces()[0]);
    }


    private void onFirstContextStartingEvent(@Observes @ContextName("first") CamelContextStartingEvent event, @ContextName("first") List<Class> events) {
        events.add(CamelContextStartingEvent.class);
    }

    private void onFirstContextStartedEvent(@Observes @ContextName("first") CamelContextStartedEvent event, @ContextName("first") List<Class> events) {
        events.add(CamelContextStartedEvent.class);
    }

    private void onFirstExchangeEvent(@Observes @ContextName("first") ExchangeEvent event, @ContextName("first") List<Class> events) {
        events.add(event.getClass().getInterfaces()[0]);
    }


    private void onSecondContextStartingEvent(@Observes @ContextName("second") CamelContextStartingEvent event, @ContextName("second") List<Class> events) {
        events.add(CamelContextStartingEvent.class);
    }

    private void onSecondContextStartedEvent(@Observes @ContextName("second") CamelContextStartedEvent event, @ContextName("second") List<Class> events) {
        events.add(CamelContextStartedEvent.class);
    }

    private void onSecondExchangeEvent(@Observes @ContextName("second") ExchangeEvent event, @ContextName("second") List<Class> events) {
        events.add(event.getClass().getInterfaces()[0]);
    }

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(
                DefaultCamelContextBean.class,
                UriEndpointRoute.class,
                FirstCamelContextBean.class,
                FirstCamelContextRoute.class,
                SecondCamelContextBean.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    @InSequence(1)
    public void configureCamelContexts(@Named("defaultContext") List<Class> defaultEvents,
                                       @ContextName("first") List<Class> firstEvents,
                                       @ContextName("second") List<Class> secondEvents,
                                       @Named("anyContext") List<Class> anyEvents) throws Exception {
        secondCamelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:inbound").setHeader("context").constant("second").to("mock:outbound");
            }
        });

        secondCamelContext.getRouteController().startAllRoutes();

        assertThat("Events fired for any contexts are incorrect", anyEvents,
            everyItem(
                isOneOf(
                    CamelContextStartingEvent.class,
                    CamelContextStartedEvent.class)));
        assertThat("Events fired for default context are incorrect", defaultEvents,
            contains(
                CamelContextStartingEvent.class,
                CamelContextStartedEvent.class));
        assertThat("Events fired for first context are incorrect", firstEvents,
            contains(
                CamelContextStartingEvent.class,
                CamelContextStartedEvent.class));
        assertThat("Events fired for second context are incorrect", secondEvents,
            contains(
                CamelContextStartingEvent.class,
                CamelContextStartedEvent.class));
    }

    @Test
    @InSequence(2)
    public void sendMessageToDefaultCamelContextInbound(@Named("defaultContext") List<Class> events) throws InterruptedException {
        defaultOutbound.expectedMessageCount(1);
        defaultOutbound.expectedBodiesReceived("test-default");
        defaultOutbound.message(0).exchange().matches(fromCamelContext("camel-cdi"));

        defaultInbound.sendBody("test-default");

        assertIsSatisfied(2L, TimeUnit.SECONDS, defaultOutbound);

        assertThat("Events fired are incorrect", events,
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
    public void sendMessageToFirstCamelContextInbound(@ContextName("first") List<Class> events) throws InterruptedException {
        firstOutbound.expectedMessageCount(1);
        firstOutbound.expectedBodiesReceived("test-first");
        firstOutbound.expectedHeaderReceived("context", "first");
        firstOutbound.message(0).exchange().matches(fromCamelContext("first"));

        firstInbound.sendBody("test-first");

        assertIsSatisfied(2L, TimeUnit.SECONDS, firstOutbound);

        assertThat("Events fired are incorrect", events,
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
    @InSequence(4)
    public void sendMessageToSecondCamelContextInbound(@ContextName("second") List<Class> events) throws InterruptedException {
        secondOutbound.expectedMessageCount(1);
        secondOutbound.expectedBodiesReceived("test-second");
        secondOutbound.expectedHeaderReceived("context", "second");
        secondOutbound.message(0).exchange().matches(fromCamelContext("second"));

        secondInbound.sendBody("test-second");

        assertIsSatisfied(2L, TimeUnit.SECONDS, secondOutbound);

        assertThat("Events fired are incorrect", events,
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
    @InSequence(5)
    public void stopCamelContexts(@Named("defaultContext") List<Class> defaultEvents,
                                  @ContextName("first") List<Class> firstEvents,
                                  @ContextName("second") List<Class> secondEvents,
                                  @Named("anyContext") List<Class> anyEvents) throws Exception {
        defaultCamelContext.stop();
        firstCamelContext.stop();
        secondCamelContext.stop();

        assertThat("Events count fired for default context are incorrect", defaultEvents, hasSize(8));
        assertThat("Events count fired for first context are incorrect", firstEvents, hasSize(8));
        assertThat("Events count fired for second context are incorrect", secondEvents, hasSize(8));
        assertThat("Events count fired for any contexts are incorrect", anyEvents, hasSize(24));
    }
}
