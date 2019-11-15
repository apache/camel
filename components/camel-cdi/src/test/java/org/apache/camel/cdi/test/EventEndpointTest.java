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
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.EventConsumingRouteCdi10;
import org.apache.camel.cdi.bean.EventProducingRouteCdi10;
import org.apache.camel.cdi.pojo.EventPayload;
import org.apache.camel.cdi.pojo.EventPayloadInteger;
import org.apache.camel.cdi.pojo.EventPayloadString;
import org.apache.camel.cdi.qualifier.BarQualifier;
import org.apache.camel.cdi.qualifier.FooQualifier;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
public class EventEndpointTest {

    @Inject
    @Uri("mock:consumeObject")
    private MockEndpoint consumeObject;

    @Inject
    @Uri("mock:consumeString")
    private MockEndpoint consumeString;

    @Inject
    @Uri("mock:consumeStringPayload")
    private MockEndpoint consumeStringPayload;

    @Inject
    @Uri("mock:consumeIntegerPayload")
    private MockEndpoint consumeIntegerPayload;

    @Inject
    @Uri("mock:consumeFooQualifier")
    private MockEndpoint consumeFooQualifier;

    @Inject
    @Uri("mock:consumeBarQualifier")
    private MockEndpoint consumeBarQualifier;

    @Inject
    @Uri("direct:produceObject")
    private ProducerTemplate produceObject;

    @Inject
    @Uri("direct:produceString")
    private ProducerTemplate produceString;

    @Inject
    @Uri("direct:produceStringPayload")
    private ProducerTemplate produceStringPayload;

    @Inject
    @Uri("direct:produceIntegerPayload")
    private ProducerTemplate produceIntegerPayload;

    @Inject
    @Uri("direct:produceFooQualifier")
    private ProducerTemplate produceFooQualifier;

    @Inject
    @Uri("direct:produceBarQualifier")
    private ProducerTemplate produceBarQualifier;

    @Inject
    private Event<Object> objectEvent;

    @Inject
    private Event<EventPayload<String>> stringPayloadEvent;

    @Inject
    private Event<EventPayload<Integer>> integerPayloadEvent;

    @Inject
    private EventObserver observer;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(
                EventConsumingRouteCdi10.class,
                EventProducingRouteCdi10.class,
                EventPayloadString.class,
                EventPayloadInteger.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Before
    public void resetCollectedEventsAndMockEndpoints() {
        observer.reset();
        consumeObject.reset();
        consumeString.reset();
        consumeStringPayload.reset();
        consumeIntegerPayload.reset();
        consumeFooQualifier.reset();
        consumeBarQualifier.reset();
    }

    @Test
    public void sendEventsToConsumers() throws InterruptedException {
        consumeObject.expectedMessageCount(8);
        consumeObject.expectedBodiesReceived(1234, new EventPayloadString("foo"), new EventPayloadString("bar"), "test", new EventPayloadInteger(1), new EventPayloadInteger(2), 123L, 987L);

        consumeString.expectedMessageCount(1);
        consumeString.expectedBodiesReceived("test");

        consumeStringPayload.expectedMessageCount(2);
        consumeStringPayload.expectedBodiesReceived(new EventPayloadString("foo"), new EventPayloadString("bar"));

        consumeIntegerPayload.expectedMessageCount(2);
        consumeIntegerPayload.expectedBodiesReceived(new EventPayloadInteger(1), new EventPayloadInteger(2));

        consumeFooQualifier.expectedMessageCount(1);
        consumeFooQualifier.expectedBodiesReceived(123L);

        consumeBarQualifier.expectedMessageCount(1);
        consumeBarQualifier.expectedBodiesReceived(987L);

        objectEvent.select(Integer.class).fire(1234);
        objectEvent.select(EventPayloadString.class).fire(new EventPayloadString("foo"));
        stringPayloadEvent.select(new BarQualifier.Literal()).fire(new EventPayloadString("bar"));
        objectEvent.select(String.class).fire("test");
        integerPayloadEvent.fire(new EventPayloadInteger(1));
        integerPayloadEvent.fire(new EventPayloadInteger(2));
        objectEvent.select(Long.class, new FooQualifier.Literal()).fire(123L);
        objectEvent.select(Long.class, new BarQualifier.Literal()).fire(987L);

        //assertIsSatisfied(2L, TimeUnit.SECONDS, consumeObject, consumeString, consumeStringPayload, consumeIntegerPayload, consumeFooQualifier, consumeBarQualifier);
        assertIsSatisfied(2L, TimeUnit.SECONDS, consumeObject, consumeString,  consumeFooQualifier, consumeBarQualifier);
    }

    @Test
    public void sendMessagesToProducers() {
        produceObject.sendBody("string");
        EventPayload foo = new EventPayloadString("foo");
        produceStringPayload.sendBody(foo);
        produceObject.sendBody(1234);
        produceString.sendBody("test");
        EventPayload<Integer> bar = new EventPayloadInteger(2);
        produceIntegerPayload.sendBody(bar);
        EventPayload<Integer> baz = new EventPayloadInteger(12);
        produceIntegerPayload.sendBody(baz);
        produceFooQualifier.sendBody(456L);
        produceBarQualifier.sendBody(495L);
        produceObject.sendBody(777L);

        assertThat(observer.getObjectEvents(), contains("string", foo, 1234, "test", bar, baz, 456L, 495L, 777L));
        // assertThat(observer.getStringEvents(), contains("string", "test"));
        assertThat(observer.getStringPayloadEvents(), contains(foo));
        assertThat(observer.getIntegerPayloadEvents(), contains(bar, baz));
        assertThat(observer.getDefaultQualifierEvents(), contains("string", foo, 1234, "test", bar, baz, 777L));
        assertThat(observer.getFooQualifierEvents(), contains(456L));
        assertThat(observer.getBarQualifierEvents(), contains(495L));
    }

    @ApplicationScoped
    static class EventObserver {

        private final List<Object> objectEvents = new ArrayList<>();

        private final List<Object> defaultQualifierEvents = new ArrayList<>();

        private final List<String> stringEvents = new ArrayList<>();

        private final List<EventPayloadString> stringPayloadEvents = new ArrayList<>();

        private final List<EventPayloadInteger> integerPayloadEvents = new ArrayList<>();

        private final List<Long> fooQualifierEvents = new ArrayList<>();

        private final List<Long> barQualifierEvents = new ArrayList<>();

        void collectObjectEvents(@Observes Object event) {
            objectEvents.add(event);
        }

        void collectStringEvents(@Observes String event) {
            stringEvents.add(event);
        }

        void collectStringPayloadEvents(@Observes EventPayloadString event) {
            stringPayloadEvents.add(event);
        }

        void collectIntegerPayloadEvents(@Observes EventPayloadInteger event) {
            integerPayloadEvents.add(event);
        }

        void collectDefaultQualifierEvents(@Observes @Default Object event) {
            defaultQualifierEvents.add(event);
        }

        void collectFooQualifierEvents(@Observes @FooQualifier Long event) {
            fooQualifierEvents.add(event);
        }

        void collectBarQualifierEvents(@Observes @BarQualifier Long event) {
            barQualifierEvents.add(event);
        }

        List<Object> getObjectEvents() {
            return objectEvents;
        }

        List<String> getStringEvents() {
            return stringEvents;
        }

        List<EventPayloadString> getStringPayloadEvents() {
            return stringPayloadEvents;
        }

        List<EventPayloadInteger> getIntegerPayloadEvents() {
            return integerPayloadEvents;
        }

        List<Object> getDefaultQualifierEvents() {
            return defaultQualifierEvents;
        }

        List<Long> getFooQualifierEvents() {
            return fooQualifierEvents;
        }

        List<Long> getBarQualifierEvents() {
            return barQualifierEvents;
        }

        void reset() {
            objectEvents.clear();
            stringEvents.clear();
            stringPayloadEvents.clear();
            integerPayloadEvents.clear();
            defaultQualifierEvents.clear();
            fooQualifierEvents.clear();
            barQualifierEvents.clear();
        }
    }
}
