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
package org.apache.camel.component.infinispan.embedded;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eventTypes option of the consumer, and the endpoint options that only the component uses. Runs with the default
 * embedded cache manager of the component, in this JVM.
 */
public class InfinispanEmbeddedEventTypesAndEndpointOptionsTest {

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("InfinispanEmbeddedWarnings", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.WARN) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };
    private CamelContext context;

    @BeforeEach
    void setUp() {
        appender.start();
        ((Logger) LogManager.getLogger(InfinispanEmbeddedConsumer.class)).addAppender(appender);
        ((Logger) LogManager.getLogger(InfinispanEmbeddedComponent.class)).addAppender(appender);
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
        ((Logger) LogManager.getLogger(InfinispanEmbeddedConsumer.class)).removeAppender(appender);
        ((Logger) LogManager.getLogger(InfinispanEmbeddedComponent.class)).removeAppender(appender);
        appender.stop();
    }

    private void consume(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).to("mock:events");
            }
        });
        context.start();
    }

    private BasicCache<Object, Object> cache() {
        return context.getComponent(InfinispanEmbeddedComponent.SCHEME, InfinispanEmbeddedComponent.class)
                .getManager().getCache("default");
    }

    private boolean warned(String... parts) {
        return warnings.stream().anyMatch(message -> List.of(parts).stream().allMatch(message::contains));
    }

    @Test
    public void testEventTypesWithSpaces() throws Exception {
        consume("infinispan-embedded:default?sync=true&eventTypes=CACHE_ENTRY_CREATED, CACHE_ENTRY_REMOVED");

        MockEndpoint mock = context.getEndpoint("mock:events", MockEndpoint.class);
        mock.expectedHeaderValuesReceivedInAnyOrder(InfinispanConstants.EVENT_TYPE,
                "CACHE_ENTRY_CREATED", "CACHE_ENTRY_CREATED", "CACHE_ENTRY_REMOVED", "CACHE_ENTRY_REMOVED");

        cache().put("key", "value");
        cache().remove("key");

        mock.assertIsSatisfied();
    }

    @Test
    public void testEventTypeThatTheListenerNeverReceivesIsLogged() throws Exception {
        consume("infinispan-embedded:default?eventTypes=CACHE_ENTRY_CREATED,TOPOLOGY_CHANGED");

        assertTrue(warned("TOPOLOGY_CHANGED", "never receives"), "warnings: " + warnings);
        assertTrue(warnings.stream().noneMatch(message -> message.contains("[CACHE_ENTRY_CREATED]")),
                "warnings: " + warnings);
    }

    @Test
    public void testEndpointOptionsOfTheCacheManagerAreLogged() throws Exception {
        context.start();
        context.getEndpoint("infinispan-embedded:default?flags=SKIP_CACHE_LOAD&configurationUri=infinispan.xml");
        context.getEndpoint("infinispan-embedded:other?sync=false");

        assertTrue(warned("[configurationUri, flags]", "infinispan-embedded://default"), "warnings: " + warnings);
        assertTrue(warnings.stream().noneMatch(message -> message.contains("infinispan-embedded://other")),
                "warnings: " + warnings);
    }
}
