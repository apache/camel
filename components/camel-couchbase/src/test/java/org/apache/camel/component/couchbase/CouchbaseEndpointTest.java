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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.codec.DefaultJsonSerializer;
import com.couchbase.client.java.codec.JsonSerializer;
import com.couchbase.client.java.env.ClusterEnvironment;
import org.apache.camel.CamelContext;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_COUCHBASE_PORT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    public void testBuildSqlQuerySimple() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setBucket("myBucket");
        String query = endpoint.buildSqlQuery();
        assertEquals("SELECT META().id AS __id, * FROM `myBucket`", query);
    }

    @Test
    public void testBuildSqlQueryWithCollection() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setBucket("myBucket");
        endpoint.setCollection("myCollection");
        String query = endpoint.buildSqlQuery();
        assertEquals("SELECT META().id AS __id, * FROM `myCollection`", query);
    }

    @Test
    public void testBuildSqlQueryWithLimitAndSkip() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setBucket("myBucket");
        endpoint.setLimit(10);
        endpoint.setSkip(5);
        String query = endpoint.buildSqlQuery();
        assertEquals("SELECT META().id AS __id, * FROM `myBucket` LIMIT 10 OFFSET 5", query);
    }

    @Test
    public void testBuildSqlQueryWithDescending() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setBucket("myBucket");
        endpoint.setDescending(true);
        endpoint.setLimit(10);
        String query = endpoint.buildSqlQuery();
        assertEquals("SELECT META().id AS __id, * FROM `myBucket` ORDER BY META().id DESC LIMIT 10", query);
    }

    @Test
    public void testBuildSqlQueryWithRangeKeys() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setBucket("myBucket");
        endpoint.setRangeStartKey("docA");
        endpoint.setRangeEndKey("docZ");
        String query = endpoint.buildSqlQuery();
        assertEquals(
                "SELECT META().id AS __id, * FROM `myBucket` WHERE META().id >= 'docA' AND META().id <= 'docZ'",
                query);
    }

    @Test
    public void testBuildSqlQueryWithStartKeyOnly() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setBucket("myBucket");
        endpoint.setRangeStartKey("docA");
        String query = endpoint.buildSqlQuery();
        assertEquals("SELECT META().id AS __id, * FROM `myBucket` WHERE META().id >= 'docA'", query);
    }

    @Test
    public void testBuildSqlQueryFullOptions() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setBucket("myBucket");
        endpoint.setCollection("orders");
        endpoint.setRangeStartKey("order_001");
        endpoint.setRangeEndKey("order_999");
        endpoint.setDescending(true);
        endpoint.setLimit(50);
        endpoint.setSkip(10);
        String query = endpoint.buildSqlQuery();
        assertEquals(
                "SELECT META().id AS __id, * FROM `orders`"
                     + " WHERE META().id >= 'order_001' AND META().id <= 'order_999'"
                     + " ORDER BY META().id DESC LIMIT 50 OFFSET 10",
                query);
    }

    /**
     * Verifies that the ClusterEnvironment is configured with DefaultJsonSerializer to prevent the Couchbase SDK from
     * auto-detecting non-shaded Jackson on the classpath (CAMEL-22090). When non-shaded Jackson is present (e.g., via
     * camel-jackson, Spring Boot, or Quarkus), auto-detection would cause the SDK to use JacksonJsonSerializer, leading
     * to deserialization failures for SDK internal types that depend on shaded Jackson classes.
     */
    @Test
    public void testClusterEnvironmentUsesDefaultJsonSerializer() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        ClusterEnvironment env = endpoint.createClusterEnvironment();
        try {
            JsonSerializer serializer = env.jsonSerializer();
            // The serializer must not be JacksonJsonSerializer since that would fail
            // when a non-shaded Jackson is on the classpath. It should be wrapped in
            // JsonValueSerializerWrapper which delegates to DefaultJsonSerializer.
            assertEquals("com.couchbase.client.java.codec.JsonValueSerializerWrapper",
                    serializer.getClass().getName(),
                    "ClusterEnvironment should wrap DefaultJsonSerializer in JsonValueSerializerWrapper");
        } finally {
            env.shutdown();
        }
    }

    /**
     * connectTimeout used to sit inside a guard that tested queryTimeout, so setting it on its own did nothing and the
     * documented 30s default never applied - the SDK's own 10s did.
     */
    @Test
    void connectTimeoutIsAppliedOnItsOwn() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setConnectTimeout(1234);
        ClusterEnvironment env = endpoint.createClusterEnvironment();
        try {
            assertEquals(Duration.ofMillis(1234), env.timeoutConfig().connectTimeout());
        } finally {
            env.shutdown();
        }
    }

    @Test
    void connectTimeoutDefaultIsApplied() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        ClusterEnvironment env = endpoint.createClusterEnvironment();
        try {
            assertEquals(Duration.ofMillis(CouchbaseConstants.DEFAULT_CONNECT_TIMEOUT),
                    env.timeoutConfig().connectTimeout());
        } finally {
            env.shutdown();
        }
    }

    @Test
    void queryTimeoutIsAppliedWhenSet() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();
        endpoint.setQueryTimeout(9999);
        ClusterEnvironment env = endpoint.createClusterEnvironment();
        try {
            assertEquals(Duration.ofMillis(9999), env.timeoutConfig().queryTimeout());
        } finally {
            env.shutdown();
        }
    }

    /**
     * The connection used to be opened once per producer and once per consumer and never closed at all: both sides
     * called {@code bucket.core().shutdown()}, which returns a cold Mono nobody subscribed to.
     */
    @Test
    void oneConnectionPerEndpointAndItIsClosedOnStop() throws Exception {
        Cluster cluster = mock(Cluster.class);
        Bucket bucket = mock(Bucket.class);
        Scope scope = mock(Scope.class);
        when(cluster.bucket(anyString())).thenReturn(bucket);
        when(bucket.defaultScope()).thenReturn(scope);
        when(bucket.defaultCollection()).thenReturn(mock(Collection.class));

        try (MockedStatic<Cluster> clusters = mockStatic(Cluster.class)) {
            clusters.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenReturn(cluster);

            CamelContext context = new DefaultCamelContext();
            CouchbaseEndpoint endpoint = new CouchbaseEndpoint(
                    "couchbase:http://localhost:8091",
                    "http://localhost:8091", new CouchbaseComponent(context));
            endpoint.setBucket("bucket");
            endpoint.setUsername("user");
            endpoint.setPassword("secret");
            endpoint.start();

            endpoint.createProducer();
            endpoint.createProducer();

            clusters.verify(() -> Cluster.connect(anyString(), any(ClusterOptions.class)), times(1));
            verify(cluster, never()).disconnect();

            endpoint.stop();
            verify(cluster).disconnect();
        }
    }

    /**
     * The endpoint disconnects its cluster on stop, so a consumer or producer that kept a handle from before the
     * restart would be talking to a dead cluster. Both take their handles again on start.
     */
    @Test
    void aRestartedProducerTakesItsCollectionFromTheNewConnection() throws Exception {
        Bucket first = mock(Bucket.class);
        Bucket second = mock(Bucket.class);
        when(first.defaultCollection()).thenReturn(mock(Collection.class));
        when(second.defaultCollection()).thenReturn(mock(Collection.class));

        Cluster one = mock(Cluster.class);
        Cluster two = mock(Cluster.class);
        when(one.bucket(anyString())).thenReturn(first);
        when(two.bucket(anyString())).thenReturn(second);

        try (MockedStatic<Cluster> clusters = mockStatic(Cluster.class)) {
            clusters.when(() -> Cluster.connect(anyString(), any(ClusterOptions.class))).thenReturn(one, two);

            CamelContext context = new DefaultCamelContext();
            CouchbaseEndpoint endpoint = new CouchbaseEndpoint(
                    "couchbase:http://localhost:8091",
                    "http://localhost:8091", new CouchbaseComponent(context));
            endpoint.setBucket("bucket");
            endpoint.setUsername("user");
            endpoint.setPassword("secret");

            endpoint.start();
            Producer producer = endpoint.createProducer();
            producer.start();
            // resolved twice against the first connection: once when constructed, once on start
            verify(first, atLeastOnce()).defaultCollection();

            endpoint.stop();
            verify(one).disconnect();

            // restart in place: the producer object survives, the connection behind it does not
            endpoint.start();
            producer.stop();
            producer.start();

            verify(second).defaultCollection();
        }
    }
}
