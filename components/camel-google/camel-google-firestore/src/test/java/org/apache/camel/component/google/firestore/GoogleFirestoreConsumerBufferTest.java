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
package org.apache.camel.component.google.firestore;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies how the realtime listener buffers the document changes it reports. Uses direct object construction to avoid
 * starting the endpoint, which requires Google Cloud credentials.
 */
class GoogleFirestoreConsumerBufferTest {

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private GoogleFirestoreConsumer consumer(int maxPendingChanges) {
        context = new DefaultCamelContext();

        GoogleFirestoreConfiguration configuration = new GoogleFirestoreConfiguration();
        configuration.setCollectionName("users");
        configuration.setRealtimeUpdates(true);
        configuration.setMaxPendingChanges(maxPendingChanges);

        GoogleFirestoreComponent component = new GoogleFirestoreComponent(context);
        GoogleFirestoreEndpoint endpoint = new GoogleFirestoreEndpoint("google-firestore:users", component, configuration);

        return new GoogleFirestoreConsumer(endpoint, exchange -> {
        });
    }

    private Exchange change(GoogleFirestoreConsumer consumer, String documentId) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, documentId);
        consumer.bufferChange(exchange);
        return exchange;
    }

    private List<String> bufferedDocumentIds(GoogleFirestoreConsumer consumer) {
        return consumer.pendingChanges().stream()
                .map(exchange -> exchange.getIn().getHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID, String.class))
                .toList();
    }

    @Test
    void theBufferIsUnboundedByDefault() {
        GoogleFirestoreConsumer consumer = consumer(0);

        for (int i = 0; i < 500; i++) {
            change(consumer, "doc-" + i);
        }

        assertThat(consumer.pendingChanges()).hasSize(500);
    }

    @Test
    void theOldestChangeIsDiscardedWhenTheBufferIsFull() {
        GoogleFirestoreConsumer consumer = consumer(3);

        change(consumer, "doc-1");
        change(consumer, "doc-2");
        change(consumer, "doc-3");
        change(consumer, "doc-4");
        change(consumer, "doc-5");

        // the route is left with the most recent state of the collection
        assertThat(bufferedDocumentIds(consumer)).containsExactly("doc-3", "doc-4", "doc-5");
    }

    @Test
    void aBufferOfOneKeepsOnlyTheLastChange() {
        GoogleFirestoreConsumer consumer = consumer(1);

        change(consumer, "doc-1");
        change(consumer, "doc-2");

        assertThat(bufferedDocumentIds(consumer)).containsExactly("doc-2");
    }
}
