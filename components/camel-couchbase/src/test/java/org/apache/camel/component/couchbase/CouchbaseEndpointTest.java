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
package org.apache.camel.component.couchbase;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Processor;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_COUCHBASE_PORT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CouchbaseEndpointTest {

    @Test
    public void assertSingleton() throws Exception {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint(
                "couchbase:http://localhost/bucket", "http://localhost/bucket", new CouchbaseComponent());
        assertTrue(endpoint.isSingleton());
    }

    @Test
    public void testDefaultPortIsSet() throws Exception {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint(
                "couchbase:http://localhost/bucket", "http://localhost/bucket", new CouchbaseComponent());
        assertEquals(DEFAULT_COUCHBASE_PORT, endpoint.getPort());
    }

    @Test
    public void testHostnameRequired() {
        final CouchbaseComponent component = new CouchbaseComponent();

        assertThrows(IllegalArgumentException.class,
                () -> {
                    new CouchbaseEndpoint("couchbase:http://:80/bucket", "couchbase://:80/bucket", component);
                });
    }

    @Test
    public void testSchemeRequired() {
        final CouchbaseComponent component = new CouchbaseComponent();

        assertThrows(IllegalArgumentException.class,
                () -> {
                    new CouchbaseEndpoint("couchbase:localhost:80/bucket", "localhost:80/bucket", component);
                });
    }

    @Test
    public void testCouchbaseEndpoint() {
        assertDoesNotThrow(() -> new CouchbaseEndpoint());
    }

    @Test
    public void testCouchbaseEndpointWithoutProtocol() {
        final CouchbaseComponent component = new CouchbaseComponent();

        assertThrows(IllegalArgumentException.class,
                () -> {
                    new CouchbaseEndpoint("localhost:80/bucket", "localhost:80/bucket", component);
                });
    }

    @Test
    public void testCouchbaseEndpointUri() {
        assertDoesNotThrow(() -> new CouchbaseEndpoint("couchbase:localhost:80/bucket", new CouchbaseComponent()));
    }

    @Test
    public void testCouchbaseEndpointCreateProducer() {
        Map<String, Object> params = new HashMap<>();
        params.put("bucket", "bucket");

        CouchbaseComponent component = new CouchbaseComponent();

        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("couchbase:localhost:80/bucket", "couchbase:localhost:80/bucket", params));
    }

    @Test
    public void testCouchbaseEndpointCreateConsumer() {
        Processor p = exchange -> {
            // Nothing to do
        };
        Map<String, Object> params = new HashMap<>();
        params.put("bucket", "bucket");

        CouchbaseComponent component = new CouchbaseComponent();

        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("couchbase:localhost:80/bucket", "couchbase:localhost:80/bucket", params));
    }

    @Test
    public void testCouchbaseEndpointSettersAndGetters() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();

        endpoint.setProtocol("couchbase");
        assertEquals("couchbase", endpoint.getProtocol());

        endpoint.setBucket("bucket");
        assertEquals("bucket", endpoint.getBucket());

        endpoint.setCollection("collection");
        assertEquals("collection", endpoint.getCollection());

        endpoint.setScope("scope");
        assertEquals("scope", endpoint.getScope());

        endpoint.setHostname("localhost");
        assertEquals("localhost", endpoint.getHostname());

        endpoint.setPort(80);
        assertEquals(80, endpoint.getPort());

        endpoint.setOperation("PUT");
        assertEquals("PUT", endpoint.getOperation());

        endpoint.setStartingIdForInsertsFrom(1L);
        assertEquals(1L, endpoint.getStartingIdForInsertsFrom());

        endpoint.setProducerRetryAttempts(5);
        assertEquals(5, endpoint.getProducerRetryAttempts());

        endpoint.setProducerRetryPause(1);
        assertEquals(1, endpoint.getProducerRetryPause());

        endpoint.setDesignDocumentName("beer");
        assertEquals("beer", endpoint.getDesignDocumentName());

        endpoint.setViewName("brewery_beers");
        assertEquals("brewery_beers", endpoint.getViewName());

        endpoint.setLimit(1);
        assertEquals(1, endpoint.getLimit());

        endpoint.setSkip(1);
        assertEquals(1, endpoint.getSkip());

        endpoint.setRangeStartKey("");
        assertEquals("", endpoint.getRangeStartKey());

        endpoint.setRangeEndKey("");
        assertEquals("", endpoint.getRangeEndKey());

        endpoint.setConsumerProcessedStrategy("delete");
        assertEquals("delete", endpoint.getConsumerProcessedStrategy());

        endpoint.setQueryTimeout(1L);
        assertEquals(1L, endpoint.getQueryTimeout());

        endpoint.setDescending(false);
    }
}
