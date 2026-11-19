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
package org.apache.camel.component.google.pubsub.unit;

import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import org.apache.camel.Exchange;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubProducer;
import org.apache.camel.component.google.pubsub.serializer.DefaultGooglePubsubSerializer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The publishers of the component are cached per endpoint, as each is built from the settings of its endpoint, and a
 * failed publish with an ordering key does not leave that key paused.
 */
class PubsubPublisherCacheTest {

    private static final String TOPIC = "projects/project/topics/orders";

    private final DefaultCamelContext context = new DefaultCamelContext();
    private GooglePubsubComponent component;

    @BeforeEach
    void setUp() {
        component = new GooglePubsubComponent();
        // a local address with no credentials, so the publishers are built without Google Cloud
        component.setEndpoint("localhost:1");
        component.setAuthenticate(false);
        component.setPublisherCacheSize(2);
        context.addComponent("google-pubsub", component);
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void endpointsWithOtherSettingsGetTheirOwnPublisher() throws Exception {
        GooglePubsubEndpoint plain = endpoint("google-pubsub:project:orders");
        GooglePubsubEndpoint ordered = endpoint(
                "google-pubsub:project:orders?messageOrderingEnabled=true&pubsubEndpoint=europe-west1-pubsub.googleapis.com:443");

        Publisher first = component.getPublisher(TOPIC, plain);

        assertThat(component.getPublisher(TOPIC, ordered)).isNotSameAs(first);
        assertThat(component.getPublisher(TOPIC, plain)).isSameAs(first);
    }

    @Test
    void theConfiguredCacheSizeIsUsed() throws Exception {
        GooglePubsubEndpoint a = endpoint("google-pubsub:project:a");
        GooglePubsubEndpoint b = endpoint("google-pubsub:project:b");
        GooglePubsubEndpoint c = endpoint("google-pubsub:project:c");

        Publisher first = component.getPublisher("projects/project/topics/a", a);
        component.getPublisher("projects/project/topics/b", b);
        component.getPublisher("projects/project/topics/c", c);

        // with room for 2 publishers, the one of a was evicted (and shut down)
        assertThat(component.getPublisher("projects/project/topics/a", a)).isNotSameAs(first);
    }

    @Test
    void aFailedOrderedPublishResumesTheOrderingKey() throws Exception {
        Publisher publisher = publishFails(true);

        verify(publisher).resumePublish("customer-1");
    }

    @Test
    void aFailedPublishWithoutOrderingDoesNotResume() throws Exception {
        Publisher publisher = publishFails(false);

        verify(publisher, never()).resumePublish(anyString());
    }

    private Publisher publishFails(boolean ordering) throws Exception {
        GooglePubsubEndpoint endpoint = mock();
        GooglePubsubComponent mockComponent = mock();
        Publisher publisher = mock();
        when(endpoint.getProjectId()).thenReturn("project");
        when(endpoint.getDestinationName()).thenReturn("orders");
        when(endpoint.getComponent()).thenReturn(mockComponent);
        when(endpoint.getSerializer()).thenReturn(new DefaultGooglePubsubSerializer());
        when(endpoint.isMessageOrderingEnabled()).thenReturn(ordering);
        when(mockComponent.getPublisher(anyString(), any())).thenReturn(publisher);
        when(publisher.publish(any())).thenReturn(ApiFutures.immediateFailedFuture(new IllegalStateException("down")));

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("order");
        exchange.getIn().setHeader(GooglePubsubConstants.ORDERING_KEY, "customer-1");

        assertThatThrownBy(() -> new GooglePubsubProducer(endpoint).process(exchange)).hasRootCauseMessage("down");
        return publisher;
    }

    private GooglePubsubEndpoint endpoint(String uri) {
        return context.getEndpoint(uri, GooglePubsubEndpoint.class);
    }
}
