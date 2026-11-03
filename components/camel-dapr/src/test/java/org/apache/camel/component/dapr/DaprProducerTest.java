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
package org.apache.camel.component.dapr;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.GetBulkSecretRequest;
import io.dapr.client.domain.GetConfigurationRequest;
import io.dapr.client.domain.GetSecretRequest;
import io.dapr.client.domain.HttpExtension;
import io.dapr.client.domain.InvokeBindingRequest;
import io.dapr.client.domain.PublishEventRequest;
import io.dapr.client.domain.StateOptions;
import io.dapr.utils.TypeRef;
import io.dapr.workflows.client.DaprWorkflowClient;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The producer, called through the endpoint with a mocked Dapr client.
 */
class DaprProducerTest extends CamelTestSupport {

    private static final String CLIENTS = "client=#myClient&previewClient=#myPreviewClient&workflowClient=#myWorkflowClient";

    private final DaprClient client = mock(DaprClient.class);
    private final DaprPreviewClient previewClient = mock(DaprPreviewClient.class);
    private final DaprWorkflowClient workflowClient = mock(DaprWorkflowClient.class);

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myClient", client);
        registry.bind("myPreviewClient", previewClient);
        registry.bind("myWorkflowClient", workflowClient);
    }

    @Test
    void getsSecret() {
        Map<String, String> secret = Map.of("mySecret", "myValue");
        when(client.getSecret(any(GetSecretRequest.class))).thenReturn(Mono.just(secret));

        Exchange exchange = template.request("dapr:secret?secretStore=myStore&key=mySecret&" + CLIENTS,
                e -> e.getIn().setHeader("foo", "bar"));

        assertNull(exchange.getException());
        assertEquals(secret, exchange.getMessage().getBody());
        assertEquals("bar", exchange.getMessage().getHeader("foo"));
        ArgumentCaptor<GetSecretRequest> request = ArgumentCaptor.forClass(GetSecretRequest.class);
        verify(client).getSecret(request.capture());
        assertEquals("myStore", request.getValue().getStoreName());
        assertEquals("mySecret", request.getValue().getKey());
    }

    @Test
    void getsBulkSecret() {
        Map<String, Map<String, String>> secrets = Map.of("mySecret", Map.of("mySecret", "myValue"));
        when(client.getBulkSecret(any(GetBulkSecretRequest.class))).thenReturn(Mono.just(secrets));

        Exchange exchange = template.request("dapr:secret?secretStore=myStore&" + CLIENTS, e -> {
        });

        assertNull(exchange.getException());
        assertEquals(secrets, exchange.getMessage().getBody());
    }

    @Test
    void keepsTheHeadersOfTheMessageAndAddsTheResponseHeaders() {
        Map<String, ConfigurationItem> config = Map.of("myKey", new ConfigurationItem("myKey", "myValue", "1"));
        when(client.getConfiguration(any(GetConfigurationRequest.class))).thenReturn(Mono.just(config));

        Exchange exchange = template.request("dapr:configuration?configStore=myStore&configKeys=myKey&" + CLIENTS,
                e -> e.getIn().setHeader("foo", "bar"));

        assertNull(exchange.getException());
        assertEquals(Map.of("myKey", "myValue"), exchange.getMessage().getBody());
        assertEquals("bar", exchange.getMessage().getHeader("foo"));
        assertSame(config, exchange.getMessage().getHeader(DaprConstants.RAW_CONFIG_RESPONSE));

        // the headers can still be changed afterwards
        exchange.getMessage().setHeader("other", "value");
        assertEquals("value", exchange.getMessage().getHeader("other"));
    }

    @Test
    void sendsTextBodyAsBytesToInvokeService() {
        when(client.invokeMethod(eq("myService"), eq("myMethod"), any(), any(HttpExtension.class), eq(byte[].class)))
                .thenReturn(Mono.just("ok".getBytes(StandardCharsets.UTF_8)));

        Exchange exchange = template.request(
                "dapr:invokeService?serviceToInvoke=myService&methodToInvoke=myMethod&" + CLIENTS,
                e -> e.getIn().setBody("hello"));

        assertNull(exchange.getException());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(client).invokeMethod(eq("myService"), eq("myMethod"), payload.capture(), any(HttpExtension.class),
                eq(byte[].class));
        assertBytes("hello", payload.getValue());
    }

    @Test
    void sendsStreamBodyAsBytesToInvokeService() {
        when(client.invokeMethod(eq("myService"), eq("myMethod"), any(), any(HttpExtension.class), eq(byte[].class)))
                .thenReturn(Mono.just("ok".getBytes(StandardCharsets.UTF_8)));

        Exchange exchange = template.request(
                "dapr:invokeService?serviceToInvoke=myService&methodToInvoke=myMethod&" + CLIENTS,
                e -> e.getIn().setBody(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))));

        assertNull(exchange.getException());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(client).invokeMethod(eq("myService"), eq("myMethod"), payload.capture(), any(HttpExtension.class),
                eq(byte[].class));
        assertBytes("hello", payload.getValue());
    }

    @Test
    void sendsMapBodyAsItIsToInvokeService() {
        when(client.invokeMethod(eq("myService"), eq("myMethod"), any(), any(HttpExtension.class), eq(byte[].class)))
                .thenReturn(Mono.just("ok".getBytes(StandardCharsets.UTF_8)));
        Map<String, String> body = Map.of("name", "value");

        Exchange exchange = template.request(
                "dapr:invokeService?serviceToInvoke=myService&methodToInvoke=myMethod&" + CLIENTS,
                e -> e.getIn().setBody(body));

        assertNull(exchange.getException());
        verify(client).invokeMethod(eq("myService"), eq("myMethod"), eq(body), any(HttpExtension.class),
                eq(byte[].class));
    }

    @Test
    void sendsTextBodyAsBytesToPubSub() {
        when(client.publishEvent(any(PublishEventRequest.class))).thenReturn(Mono.empty());

        Exchange exchange = template.request("dapr:pubSub?pubSubName=myPubSub&topic=myTopic&" + CLIENTS,
                e -> e.getIn().setBody("hello"));

        assertNull(exchange.getException());
        ArgumentCaptor<PublishEventRequest> request = ArgumentCaptor.forClass(PublishEventRequest.class);
        verify(client).publishEvent(request.capture());
        assertBytes("hello", request.getValue().getData());
    }

    @Test
    void sendsTextBodyAsBytesToInvokeBinding() {
        when(client.invokeBinding(any(InvokeBindingRequest.class), any(TypeRef.class))).thenReturn(Mono.empty());

        Exchange exchange = template.request("dapr:invokeBinding?bindingName=myBinding&bindingOperation=create&" + CLIENTS,
                e -> e.getIn().setBody("hello"));

        assertNull(exchange.getException());
        ArgumentCaptor<InvokeBindingRequest> request = ArgumentCaptor.forClass(InvokeBindingRequest.class);
        verify(client).invokeBinding(request.capture(), any(TypeRef.class));
        assertBytes("hello", request.getValue().getData());
    }

    @Test
    void savesTextBodyAsBytes() {
        when(client.saveState(eq("myStore"), eq("myKey"), isNull(), any(), (StateOptions) isNull())).thenReturn(Mono.empty());

        Exchange exchange = template.request("dapr:state?stateOperation=save&stateStore=myStore&key=myKey&" + CLIENTS,
                e -> e.getIn().setBody("hello"));

        assertNull(exchange.getException());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(client).saveState(eq("myStore"), eq("myKey"), isNull(), payload.capture(), (StateOptions) isNull());
        assertBytes("hello", payload.getValue());
    }

    private static void assertBytes(String expected, Object actual) {
        byte[] bytes = assertInstanceOf(byte[].class, actual);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), bytes);
    }
}
