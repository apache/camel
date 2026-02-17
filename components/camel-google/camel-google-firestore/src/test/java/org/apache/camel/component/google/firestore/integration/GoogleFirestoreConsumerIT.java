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
package org.apache.camel.component.google.firestore.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.firestore.GoogleFirestoreConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Google Firestore Consumer operations.
 *
 * <p>
 * These tests require valid Google Cloud credentials. To run:
 * </p>
 *
 * <pre>
 * mvn verify -pl components/camel-google/camel-google-firestore \
 *     -Dgoogle.firestore.serviceAccountKey=/path/to/service-account.json \
 *     -Dgoogle.firestore.projectId=my-project-id
 * </pre>
 */
@EnabledIf(value = "org.apache.camel.component.google.firestore.integration.GoogleFirestoreITSupport#hasCredentials",
           disabledReason = "Google Firestore credentials not provided. Set google.firestore.serviceAccountKey and google.firestore.projectId system properties.")
public class GoogleFirestoreConsumerIT extends GoogleFirestoreITSupport {

    @Test
    void testConsumeDocuments() throws Exception {
        // First, create some documents to consume
        String collectionName = getTestCollection() + "-consumer";

        for (int i = 0; i < 3; i++) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("message", "Consumer test document " + i);
            doc.put("index", i);

            template.sendBodyAndHeaders("direct:createForConsumer", doc, Map.of(
                    GoogleFirestoreConstants.COLLECTION_NAME, collectionName,
                    GoogleFirestoreConstants.DOCUMENT_ID, "consumer-doc-" + i));
        }

        // Wait for documents to be available
        Thread.sleep(2000);

        MockEndpoint mock = getMockEndpoint("mock:consumed");
        mock.expectedMinimumMessageCount(3);
        mock.await(30, TimeUnit.SECONDS);

        assertTrue(mock.getReceivedCounter() >= 3, "Should have consumed at least 3 documents");

        // Verify document structure
        mock.getExchanges().forEach(exchange -> {
            assertNotNull(exchange.getMessage().getHeader(GoogleFirestoreConstants.RESPONSE_DOCUMENT_ID));
            assertNotNull(exchange.getMessage().getBody(Map.class));
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String consumerCollection = getTestCollection() + "-consumer";

        return new RouteBuilder() {
            @Override
            public void configure() {
                // Producer route to create documents
                from("direct:createForConsumer")
                        .toD("google-firestore:${header.CamelGoogleFirestoreCollectionName}?operation=setDocument");

                // Consumer route to poll documents
                from("google-firestore:" + consumerCollection + "?delay=5000")
                        .to("mock:consumed");
            }
        };
    }
}
