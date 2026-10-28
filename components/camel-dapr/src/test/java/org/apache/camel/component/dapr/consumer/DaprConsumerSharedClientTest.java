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
package org.apache.camel.component.dapr.consumer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.Subscription;
import io.dapr.client.SubscriptionListener;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.SubscribeConfigurationRequest;
import io.dapr.client.domain.SubscribeConfigurationResponse;
import io.dapr.client.domain.UnsubscribeConfigurationResponse;
import io.dapr.utils.TypeRef;
import io.dapr.workflows.client.DaprWorkflowClient;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The Dapr clients in the registry are autowired and shared by all the dapr endpoints: a consumer must not close them
 * when it stops, and must be able to consume again when its route is started again.
 */
class DaprConsumerSharedClientTest extends CamelTestSupport {

    private final DaprClient client = mock(DaprClient.class);
    private final DaprPreviewClient previewClient = mock(DaprPreviewClient.class);
    private final DaprWorkflowClient workflowClient = mock(DaprWorkflowClient.class);

    private final List<FluxSink<SubscribeConfigurationResponse>> configSubscriptions = new CopyOnWriteArrayList<>();
    private final AtomicBoolean configSubscriptionCancelled = new AtomicBoolean();
    private final List<String> unsubscribed = new CopyOnWriteArrayList<>();

    @BindToRegistry("daprClient")
    public DaprClient daprClient() {
        doAnswer(inv -> Flux.<SubscribeConfigurationResponse> create(sink -> {
            sink.onCancel(() -> configSubscriptionCancelled.set(true));
            configSubscriptions.add(sink);
        })).when(client).subscribeConfiguration(any(SubscribeConfigurationRequest.class));
        doAnswer(inv -> Mono.fromCallable(() -> {
            unsubscribed.add(inv.getArgument(0));
            return new UnsubscribeConfigurationResponse(true, "");
        })).when(client).unsubscribeConfiguration(anyString(), anyString());
        return client;
    }

    @BindToRegistry("daprPreviewClient")
    public DaprPreviewClient daprPreviewClient() {
        doAnswer(inv -> mock(Subscription.class)).when(previewClient)
                .subscribeToEvents(anyString(), anyString(), any(SubscriptionListener.class), any(TypeRef.class));
        return previewClient;
    }

    @BindToRegistry("daprWorkflowClient")
    public DaprWorkflowClient daprWorkflowClient() {
        return workflowClient;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("dapr:pubSub?pubSubName=myPubSub&topic=myTopic").routeId("pubSub")
                        .to("mock:pubSub");
                from("dapr:configuration?configStore=myStore&configKeys=myKey").routeId("configuration").autoStartup(false)
                        .to("mock:configuration");
            }
        };
    }

    @Test
    void pubSubConsumerDoesNotCloseTheSharedClient() throws Exception {
        context.getRouteController().stopRoute("pubSub");

        verify(previewClient, never()).close();

        context.getRouteController().startRoute("pubSub");

        verify(previewClient, times(2)).subscribeToEvents(anyString(), anyString(), any(SubscriptionListener.class),
                any(TypeRef.class));
    }

    @Test
    void configurationConsumerUnsubscribesAndDoesNotCloseTheSharedClient() throws Exception {
        // the consumer is created before its endpoint is started (and has created or taken its client)
        context.getRouteController().startRoute("configuration");
        assertEquals(1, configSubscriptions.size());
        configSubscriptions.get(0).next(new SubscribeConfigurationResponse("sub-1", Map.of()));

        context.getRouteController().stopRoute("configuration");

        // the subscription is ended, the client stays open for the other endpoints
        assertTrue(configSubscriptionCancelled.get());
        assertEquals(List.of("sub-1"), unsubscribed);
        verify(client, never()).close();

        // and the route consumes again when it is started again
        context.getRouteController().startRoute("configuration");
        assertEquals(2, configSubscriptions.size());

        MockEndpoint mock = getMockEndpoint("mock:configuration");
        mock.expectedMessageCount(1);
        configSubscriptions.get(1).next(new SubscribeConfigurationResponse(
                "sub-2", Map.of("myKey", new ConfigurationItem("myKey", "myValue", "1"))));
        mock.assertIsSatisfied();
        assertEquals(Map.of("myKey", "myValue"), mock.getReceivedExchanges().get(0).getIn().getBody());
    }
}
