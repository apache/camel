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
package org.apache.camel.component.ibm.watsonx.data;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the WatsonxDataComponent.
 */
class WatsonxDataComponentTest {

    @Test
    void testComponentCreation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxDataComponent component = new WatsonxDataComponent();
            context.addComponent("ibm-watsonx-data", component);

            assertNotNull(context.getComponent("ibm-watsonx-data"));
        }
    }

    @Test
    void testEndpointCreation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxDataComponent component = new WatsonxDataComponent();
            context.addComponent("ibm-watsonx-data", component);

            WatsonxDataEndpoint endpoint = (WatsonxDataEndpoint) context.getEndpoint(
                    "ibm-watsonx-data:myLabel?apiKey=test-key&serviceUrl=https://test.lakehouse.cloud.ibm.com&operation=listCatalogs");

            assertNotNull(endpoint);
            assertEquals("myLabel", endpoint.getLabel());
            assertEquals("test-key", endpoint.getConfiguration().getApiKey());
            assertEquals("https://test.lakehouse.cloud.ibm.com", endpoint.getConfiguration().getServiceUrl());
            assertEquals(WatsonxDataOperations.listCatalogs, endpoint.getConfiguration().getOperation());
        }
    }

    @Test
    void testEndpointCreationWithAllParameters() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxDataComponent component = new WatsonxDataComponent();
            context.addComponent("ibm-watsonx-data", component);

            WatsonxDataEndpoint endpoint = (WatsonxDataEndpoint) context.getEndpoint(
                    "ibm-watsonx-data:myLabel?apiKey=test-key"
                                                                                     + "&serviceUrl=https://test.lakehouse.cloud.ibm.com"
                                                                                     + "&operation=listTables"
                                                                                     + "&catalogName=my-catalog"
                                                                                     + "&schemaName=my-schema"
                                                                                     + "&engineId=my-engine"
                                                                                     + "&authInstanceId=crn:v1:test");

            assertNotNull(endpoint);
            assertEquals("my-catalog", endpoint.getConfiguration().getCatalogName());
            assertEquals("my-schema", endpoint.getConfiguration().getSchemaName());
            assertEquals("my-engine", endpoint.getConfiguration().getEngineId());
            assertEquals("crn:v1:test", endpoint.getConfiguration().getAuthInstanceId());
            assertEquals(WatsonxDataOperations.listTables, endpoint.getConfiguration().getOperation());
        }
    }

    @Test
    void testConfigurationCopy() {
        WatsonxDataConfiguration config = new WatsonxDataConfiguration();
        config.setApiKey("original-key");
        config.setServiceUrl("https://original.com");
        config.setCatalogName("catalog-1");
        config.setSchemaName("schema-1");
        config.setEngineId("engine-1");

        WatsonxDataConfiguration copy = config.copy();

        assertNotNull(copy);
        assertEquals("original-key", copy.getApiKey());
        assertEquals("https://original.com", copy.getServiceUrl());
        assertEquals("catalog-1", copy.getCatalogName());
        assertEquals("schema-1", copy.getSchemaName());
        assertEquals("engine-1", copy.getEngineId());

        // Modify copy and verify original is unchanged
        copy.setApiKey("modified-key");
        assertEquals("original-key", config.getApiKey());
        assertEquals("modified-key", copy.getApiKey());
    }

    @Test
    void testOperationsEnum() {
        assertEquals(18, WatsonxDataOperations.values().length);

        // Verify catalog operations
        assertNotNull(WatsonxDataOperations.valueOf("listCatalogs"));
        assertNotNull(WatsonxDataOperations.valueOf("getCatalog"));
        assertNotNull(WatsonxDataOperations.valueOf("deleteCatalog"));

        // Verify schema operations
        assertNotNull(WatsonxDataOperations.valueOf("listSchemas"));
        assertNotNull(WatsonxDataOperations.valueOf("createSchema"));
        assertNotNull(WatsonxDataOperations.valueOf("deleteSchema"));

        // Verify table operations
        assertNotNull(WatsonxDataOperations.valueOf("listTables"));
        assertNotNull(WatsonxDataOperations.valueOf("getTable"));
        assertNotNull(WatsonxDataOperations.valueOf("deleteTable"));
        assertNotNull(WatsonxDataOperations.valueOf("updateTable"));
        assertNotNull(WatsonxDataOperations.valueOf("registerTable"));
        assertNotNull(WatsonxDataOperations.valueOf("getAllColumns"));

        // Verify engine operations
        assertNotNull(WatsonxDataOperations.valueOf("listPrestoEngines"));
        assertNotNull(WatsonxDataOperations.valueOf("getPrestoEngine"));
        assertNotNull(WatsonxDataOperations.valueOf("listPrestissimoEngines"));
        assertNotNull(WatsonxDataOperations.valueOf("getPrestissimoEngine"));

        // Verify storage operations
        assertNotNull(WatsonxDataOperations.valueOf("listStorageRegistrations"));
        assertNotNull(WatsonxDataOperations.valueOf("createStorageRegistration"));
    }

    @Test
    void testEndpointValidationMissingApiKey() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxDataComponent component = new WatsonxDataComponent();
            context.addComponent("ibm-watsonx-data", component);

            WatsonxDataEndpoint endpoint = (WatsonxDataEndpoint) context.getEndpoint(
                    "ibm-watsonx-data:test?serviceUrl=https://test.example.com");

            assertThrows(IllegalArgumentException.class, endpoint::start);
        }
    }

    @Test
    void testEndpointValidationMissingServiceUrl() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxDataComponent component = new WatsonxDataComponent();
            context.addComponent("ibm-watsonx-data", component);

            WatsonxDataEndpoint endpoint = (WatsonxDataEndpoint) context.getEndpoint(
                    "ibm-watsonx-data:test?apiKey=test-key");

            assertThrows(IllegalArgumentException.class, endpoint::start);
        }
    }

    @Test
    void testServiceLocation() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxDataComponent component = new WatsonxDataComponent();
            context.addComponent("ibm-watsonx-data", component);

            WatsonxDataEndpoint endpoint = (WatsonxDataEndpoint) context.getEndpoint(
                    "ibm-watsonx-data:test?apiKey=test-key&serviceUrl=https://us-south.lakehouse.cloud.ibm.com/lakehouse/api/v2");

            assertEquals("https://us-south.lakehouse.cloud.ibm.com/lakehouse/api/v2", endpoint.getServiceUrl());
            assertEquals("https", endpoint.getServiceProtocol());
        }
    }

    @Test
    void testConsumerNotSupported() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WatsonxDataComponent component = new WatsonxDataComponent();
            context.addComponent("ibm-watsonx-data", component);

            WatsonxDataEndpoint endpoint = (WatsonxDataEndpoint) context.getEndpoint(
                    "ibm-watsonx-data:test?apiKey=test-key&serviceUrl=https://test.example.com");

            assertThrows(UnsupportedOperationException.class, () -> endpoint.createConsumer(null));
        }
    }
}
