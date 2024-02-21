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
package org.apache.camel.component.dynamicrouter.control;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_ACTION_HEADER;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.CONTROL_SUBSCRIPTION_ID;
import static org.apache.camel.component.dynamicrouter.control.DynamicRouterControlConstants.URI_PARAMS_TO_HEADER_NAMES;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class DynamicRouterControlChannelSendDynamicAwareTest {

    @Mock
    Exchange exchange;

    @Mock
    Message message;

    @Test
    void prepare() throws Exception {
        String originalUri = "dynamic-router-control://subscribe?subscriptionId=testSub1";
        String uri = "dynamic-router-control://subscribe?subscriptionId=testSub1";
        try (DynamicRouterControlChannelSendDynamicAware testSubject = new DynamicRouterControlChannelSendDynamicAware()) {
            SendDynamicAware.DynamicAwareEntry entry = testSubject.prepare(exchange, uri, originalUri);
            assertAll(
                    () -> assertEquals(entry.getOriginalUri(), originalUri),
                    () -> assertEquals(entry.getUri(), uri),
                    () -> assertEquals(2, entry.getProperties().size()),
                    () -> assertEquals("subscribe", entry.getProperties().get("controlAction")),
                    () -> assertEquals("testSub1", entry.getProperties().get("subscriptionId")));
        }
    }

    @Test
    void resolveStaticUri() throws Exception {
        String originalUri = "dynamic-router-control:subscribe?subscriptionId=testSub1";
        String uri = "dynamic-router-control://subscribe?subscriptionId=testSub1";
        try (DynamicRouterControlChannelSendDynamicAware testSubject = new DynamicRouterControlChannelSendDynamicAware()) {
            SendDynamicAware.DynamicAwareEntry entry = testSubject.prepare(exchange, uri, originalUri);
            String result = testSubject.resolveStaticUri(exchange, entry);
            assertEquals("dynamic-router-control://subscribe", result);
        }
    }

    @Test
    void resolveStaticUriShouldNotOptimize() throws Exception {
        String originalUri = "dynamic-router-ctrl:subscribe?subscriptionId=testSub1";
        String uri = "dynamic-router-ctrl://subscribe?subscriptionId=testSub1";
        try (DynamicRouterControlChannelSendDynamicAware testSubject = new DynamicRouterControlChannelSendDynamicAware()) {
            SendDynamicAware.DynamicAwareEntry entry = testSubject.prepare(exchange, uri, originalUri);
            String result = testSubject.resolveStaticUri(exchange, entry);
            assertEquals(null, result);
        }
    }

    @Test
    void createPreProcessor() throws Exception {
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.doNothing().when(message).setHeader(CONTROL_ACTION_HEADER, "subscribe");
        Mockito.doNothing().when(message).setHeader(CONTROL_SUBSCRIPTION_ID, "testSub1");
        String originalUri = "dynamic-router-control:subscribe?subscriptionId=testSub1";
        String uri = "dynamic-router-control://subscribe?subscriptionId=testSub1";
        try (DynamicRouterControlChannelSendDynamicAware testSubject = new DynamicRouterControlChannelSendDynamicAware()) {
            SendDynamicAware.DynamicAwareEntry entry = testSubject.prepare(exchange, uri, originalUri);
            Processor preProcessor = testSubject.createPreProcessor(exchange, entry);
            Assertions.assertNotNull(preProcessor);
            preProcessor.process(exchange);
        }
    }

    @Test
    void createPreProcessorShouldNotOptimize() throws Exception {
        String originalUri = "dynamic-router-ctrl:subscribe?subscriptionId=testSub1";
        String uri = "dynamic-router-ctrl://subscribe?subscriptionId=testSub1";
        try (DynamicRouterControlChannelSendDynamicAware testSubject = new DynamicRouterControlChannelSendDynamicAware()) {
            SendDynamicAware.DynamicAwareEntry entry = testSubject.prepare(exchange, uri, originalUri);
            Processor preProcessor = testSubject.createPreProcessor(exchange, entry);
            Assertions.assertNull(preProcessor);
        }
    }

    @Test
    void createPostProcessor() throws Exception {
        Mockito.when(exchange.getMessage()).thenReturn(message);
        Mockito.when(message.removeHeader(any())).thenReturn("test");
        String originalUri = "dynamic-router-control:subscribe?subscriptionId=testSub1";
        String uri = "dynamic-router-control://subscribe?subscriptionId=testSub1";
        try (DynamicRouterControlChannelSendDynamicAware testSubject = new DynamicRouterControlChannelSendDynamicAware()) {
            SendDynamicAware.DynamicAwareEntry entry = testSubject.prepare(exchange, uri, originalUri);
            Processor postProcessor = testSubject.createPostProcessor(exchange, entry);
            postProcessor.process(exchange);
        }
        Mockito.verify(message, Mockito.times(URI_PARAMS_TO_HEADER_NAMES.size())).removeHeader(any());
    }

    @Test
    void createPostProcessorShouldNotOptimize() throws Exception {
        String originalUri = "dynamic-router-ctrl:subscribe?subscriptionId=testSub1";
        String uri = "dynamic-router-ctrl://subscribe?subscriptionId=testSub1";
        try (DynamicRouterControlChannelSendDynamicAware testSubject = new DynamicRouterControlChannelSendDynamicAware()) {
            SendDynamicAware.DynamicAwareEntry entry = testSubject.prepare(exchange, uri, originalUri);
            Processor postProcessor = testSubject.createPostProcessor(exchange, entry);
            assertNull(postProcessor);
        }
    }
}
