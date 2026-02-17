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

import java.util.HashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Google Firestore component.
 */
public class GoogleFirestoreComponentTest extends CamelTestSupport {

    @Test
    void testCreateEndpoint() throws Exception {
        GoogleFirestoreComponent component = new GoogleFirestoreComponent();
        component.setCamelContext(context);

        GoogleFirestoreEndpoint endpoint = (GoogleFirestoreEndpoint) component.createEndpoint(
                "google-firestore:myCollection",
                "myCollection",
                new HashMap<>());

        assertNotNull(endpoint);
        assertEquals("myCollection", endpoint.getConfiguration().getCollectionName());
    }

    @Test
    void testCreateEndpointWithoutCollectionName() {
        GoogleFirestoreComponent component = new GoogleFirestoreComponent();
        component.setCamelContext(context);

        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("google-firestore:", "", new HashMap<>()));
    }

    @Test
    void testConfigurationCopy() {
        GoogleFirestoreConfiguration config = new GoogleFirestoreConfiguration();
        config.setCollectionName("original");
        config.setProjectId("project1");
        config.setDatabaseId("db1");
        config.setDocumentId("doc1");
        config.setOperation(GoogleFirestoreOperations.setDocument);
        config.setRealtimeUpdates(true);

        GoogleFirestoreConfiguration copy = config.copy();

        assertEquals("original", copy.getCollectionName());
        assertEquals("project1", copy.getProjectId());
        assertEquals("db1", copy.getDatabaseId());
        assertEquals("doc1", copy.getDocumentId());
        assertEquals(GoogleFirestoreOperations.setDocument, copy.getOperation());
        assertTrue(copy.isRealtimeUpdates());

        // Modify original, copy should not change
        config.setCollectionName("modified");
        assertEquals("original", copy.getCollectionName());
    }

    @Test
    void testComponentConfiguration() {
        GoogleFirestoreComponent component = new GoogleFirestoreComponent();
        GoogleFirestoreConfiguration config = new GoogleFirestoreConfiguration();
        config.setProjectId("test-project");
        config.setDatabaseId("test-db");

        component.setConfiguration(config);

        assertNotNull(component.getConfiguration());
        assertEquals("test-project", component.getConfiguration().getProjectId());
        assertEquals("test-db", component.getConfiguration().getDatabaseId());
    }

    @Test
    void testOperationsEnum() {
        // Test all operations are defined
        assertEquals(8, GoogleFirestoreOperations.values().length);
        assertNotNull(GoogleFirestoreOperations.setDocument);
        assertNotNull(GoogleFirestoreOperations.getDocumentById);
        assertNotNull(GoogleFirestoreOperations.updateDocument);
        assertNotNull(GoogleFirestoreOperations.deleteDocument);
        assertNotNull(GoogleFirestoreOperations.queryCollection);
        assertNotNull(GoogleFirestoreOperations.listDocuments);
        assertNotNull(GoogleFirestoreOperations.listCollections);
        assertNotNull(GoogleFirestoreOperations.createDocument);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return super.createCamelContext();
    }
}
