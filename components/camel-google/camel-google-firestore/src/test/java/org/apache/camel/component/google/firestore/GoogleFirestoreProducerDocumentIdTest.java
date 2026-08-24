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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the operations resolve the document id the same way: the header first, then the endpoint option.
 */
class GoogleFirestoreProducerDocumentIdTest {

    private DefaultCamelContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    private GoogleFirestoreProducer producer(String query) throws Exception {
        if (context != null) {
            context.stop();
        }
        context = new DefaultCamelContext();
        // the endpoint is deliberately not started, so no firestore client is built
        GoogleFirestoreComponent component = context.getComponent("google-firestore", GoogleFirestoreComponent.class);
        GoogleFirestoreEndpoint endpoint
                = (GoogleFirestoreEndpoint) component.createEndpoint("google-firestore://users" + query);
        return new GoogleFirestoreProducer(endpoint);
    }

    @Test
    void theConfiguredDocumentIdIsUsedWhenNoHeaderIsSet() throws Exception {
        GoogleFirestoreProducer producer = producer("?documentId=configured");

        assertThat(producer.determineListedDocumentId(new DefaultExchange(context))).isEqualTo("configured");
    }

    @Test
    void theHeaderWinsOverTheConfiguredDocumentId() throws Exception {
        GoogleFirestoreProducer producer = producer("?documentId=configured");

        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(GoogleFirestoreConstants.DOCUMENT_ID, "from-header");

        assertThat(producer.determineListedDocumentId(exchange)).isEqualTo("from-header");
    }

    @Test
    void withoutAnyDocumentIdTheRootCollectionsAreListed() throws Exception {
        // listCollections is the one operation where the document id is optional: no id means the root
        GoogleFirestoreProducer producer = producer("");

        assertThat(producer.determineListedDocumentId(new DefaultExchange(context))).isNull();
    }
}
