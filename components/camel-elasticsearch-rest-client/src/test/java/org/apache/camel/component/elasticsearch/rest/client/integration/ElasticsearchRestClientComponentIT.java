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
package org.apache.camel.component.elasticsearch.rest.client.integration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.elasticsearch.rest.client.ElasticSearchRestClientConstant;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ElasticsearchRestClientComponentIT extends ElasticsearchRestClientITSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        this.context.getRegistry().bind("restClient", restClient);
        return new RouteBuilder() {
            public void configure() {
                from("direct:create-index")
                        .to("elasticsearch-rest-client:my-cluster?operation=CREATE_INDEX&restClient=#restClient&indexName=my_index");

                from("direct:create-index-settings")
                        .to("elasticsearch-rest-client:my-cluster?operation=CREATE_INDEX&restClient=#restClient&indexName=my_index_2");

                from("direct:delete-index")
                        .to("elasticsearch-rest-client:my-cluster?operation=DELETE_INDEX&restClient=#restClient");

                from("direct:index")
                        .to("elasticsearch-rest-client:my-cluster?operation=INDEX_OR_UPDATE&restClient=#restClient&indexName=my_index");

                from("direct:get-by-id")
                        .onException(Exception.class)
                            .handled(true) // Marks the exception as handled
                            .to("direct:errorHandler")
                        .end()
                        .to("elasticsearch-rest-client:my-cluster?operation=GET_BY_ID&restClient=#restClient&indexName=my_index");

                from("direct:search")
                        .to("elasticsearch-rest-client:my-cluster?operation=SEARCH&restClient=#restClient&indexName=my_index");

                from("direct:delete")
                        .onException(Exception.class)
                            .handled(true) // Marks the exception as handled
                            .to("direct:errorHandler")
                        .end()
                        .to("elasticsearch-rest-client:my-cluster?operation=DELETE&restClient=#restClient&indexName=my_index");

                from("direct:errorHandler")
                        .log("Handling exception: ${exception.message}")
                        .to("mock:errorHandler");
            }
        };
    }

    @Test
    void testProducer() throws ExecutionException, InterruptedException {
        // create index
        CompletableFuture<Boolean> ack = template.asyncRequestBody("direct:create-index", null, Boolean.class);
        assertTrue(ack.get());

        // index a document
        var document = " {\"title\": \"Elastic is funny\",  \"tag\": [\"lucene\" ]}";
        CompletableFuture<String> response = template.asyncRequestBody("direct:index", document, String.class);
        // get id from response document
        var id = response.get();
        assertNotNull(id);

        // Get document by id
        response = template.asyncRequestBody("direct:get-by-id", id, String.class);
        var indexedDocument = response.get();
        assertNotNull(indexedDocument);

        // index a second document, with specifying id
        var id2 = "123456";
        var document2 = " {\"title\": \"Elastic is awesome\",  \"tag\": [\"elastic\" ]}";
        response = template.asyncRequestBodyAndHeader("direct:index", document2, ElasticSearchRestClientConstant.ID, id2,
                String.class);
        // get id from response document
        response.get();

        // Get document by id
        response = template.asyncRequestBody("direct:get-by-id", id2, String.class);
        indexedDocument = response.get();
        assertNotNull(indexedDocument);

        // Get document by id that doesn't exist  - The API is supposed to raise an Exception
        // Set up expectations on the mock endpoint
        MockEndpoint mockErrorHandler = this.context.getEndpoint("mock:errorHandler", MockEndpoint.class);
        mockErrorHandler.expectedMessageCount(1);

        template.asyncRequestBody("direct:get-by-id", "nothingspecial");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Assert that the direct:get-by-id endpoint received the exception
            mockErrorHandler.assertIsSatisfied();
        });

        // perform a request to fecth all documents without any criteria
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var future = template.asyncRequestBody("direct:search", null, String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult
                    = "[{\"title\":\"Elastic is funny\",\"tag\":[\"lucene\"]},{\"title\":\"Elastic is awesome\",\"tag\":[\"elastic\"]}]";
            assertEquals(expectedResult, allDocuments);
        });

        // fetch one single document  with criterias
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, String> criterias = new HashMap<>();
            criterias.put("title", "Elastic");
            criterias.put("tag", "elastic");
            var future = template.asyncRequestBody("direct:search", criterias, String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult = "[{\"title\":\"Elastic is awesome\",\"tag\":[\"elastic\"]}]";
            assertEquals(expectedResult, allDocuments);
        });

        // fetch multiple documents  with criterias
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, String> criterias = new HashMap<>();
            criterias.put("title", "Elastic");
            var future = template.asyncRequestBody("direct:search", criterias, String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult
                    = "[{\"title\":\"Elastic is funny\",\"tag\":[\"lucene\"]},{\"title\":\"Elastic is awesome\",\"tag\":[\"elastic\"]}]";
            assertEquals(expectedResult, allDocuments);
        });

        // fetch inexisting document with criterias
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, String> criterias = new HashMap<>();
            criterias.put("title", "Apache");
            var future = template.asyncRequestBody("direct:search", criterias, String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult = "[]";
            assertEquals(expectedResult, allDocuments);
        });

        // Update a document, with specifying id
        document2 = "{\"title\": \"Elastic is awesome with Apache Camel\",  \"tag\": [\"elastic\" ]}";
        response = template.asyncRequestBodyAndHeader("direct:index", document2, ElasticSearchRestClientConstant.ID, id2,
                String.class);
        response.get();

        // fetch again with title = Apache
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, String> criterias = new HashMap<>();
            criterias.put("title", "Apache");
            var future = template.asyncRequestBody("direct:search", criterias, String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult = "[{\"title\":\"Elastic is awesome with Apache Camel\",\"tag\":[\"elastic\"]}]";
            assertEquals(expectedResult, allDocuments);
        });

        // fetch with Passed JSON QUERY - Advanced Elasticsearch QUERY that returns one value
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var query
                    = "{\"query\": {\"bool\": {\"must\": [{\"match\": {\"tag\": \"elastic\"} },{\"match\": { \"title\": \"Apache\"}}]}}}";
            var future = template.asyncRequestBodyAndHeader("direct:search", null, ElasticSearchRestClientConstant.SEARCH_QUERY,
                    query,
                    String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult = "[{\"title\":\"Elastic is awesome with Apache Camel\",\"tag\":[\"elastic\"]}]";
            assertEquals(expectedResult, allDocuments);
        });

        // fetch with Passed JSON QUERY - Advanced Elasticsearch QUERY that returns no value
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var query
                    = "{\"query\": {\"bool\": {\"must\": [{\"match\": {\"tag\": \"lucene\"} },{\"match\": { \"title\": \"HelloWorld\"}}]}}}";
            var future = template.asyncRequestBodyAndHeader("direct:search", null, ElasticSearchRestClientConstant.SEARCH_QUERY,
                    query,
                    String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult = "[]";
            assertEquals(expectedResult, allDocuments);
        });

        // DELETE one document by existing ID
        ack = template.asyncRequestBodyAndHeader("direct:delete", null, ElasticSearchRestClientConstant.ID, id2,
                Boolean.class);
        assertTrue(ack.get());

        // check is not visible in search anymore
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var query
                    = "{\"query\": {\"bool\": {\"must\": [{\"match\": {\"tag\": \"elastic\"} },{\"match\": { \"title\": \"Apache\"}}]}}}";
            var future = template.asyncRequestBodyAndHeader("direct:search", null, ElasticSearchRestClientConstant.SEARCH_QUERY,
                    query,
                    String.class);
            var allDocuments = future.get();
            assertNotNull(allDocuments);
            var expectedResult = "[]";
            assertEquals(expectedResult, allDocuments);
        });

        // DELETE a document by inexisting ID
        mockErrorHandler.reset();
        mockErrorHandler.expectedMessageCount(1);

        template.asyncRequestBody("direct:delete", "nothingspecial");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // Assert that the direct-delete endpoint received the exception
            mockErrorHandler.assertIsSatisfied();
        });

        // Create an index with settings and Mappings
        var indexSettings
                = "{\"settings\":{\"number_of_replicas\": 1,\"number_of_shards\": 3,\"analysis\": {},\"refresh_interval\": \"1s\"},\"mappings\":{\"dynamic\": false,\"properties\": {\"title\": {\"type\": \"text\", \"analyzer\": \"english\"}}}}";
        ack = template.asyncRequestBodyAndHeader("direct:create-index-settings", null,
                ElasticSearchRestClientConstant.INDEX_SETTINGS, indexSettings, Boolean.class);
        assertTrue(ack.get());

        // delete index my_index
        ack = template.asyncRequestBodyAndHeader("direct:delete-index", null, ElasticSearchRestClientConstant.INDEX_NAME,
                "my_index", Boolean.class);
        assertTrue(ack.get());

        // delete index my_index_2
        ack = template.asyncRequestBodyAndHeader("direct:delete-index", null, ElasticSearchRestClientConstant.INDEX_NAME,
                "my_index_2", Boolean.class);
        assertTrue(ack.get());
    }

}
