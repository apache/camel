/**
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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.Test;

import static org.apache.camel.component.couchbase.CouchbaseConstants.DEFAULT_COUCHBASE_PORT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class CouchbaseEndpointTest {

    @Test
    public void assertSingleton() throws Exception {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint("couchbase:http://localhost/bucket", "http://localhost/bucket", new CouchbaseComponent());
        assertTrue(endpoint.isSingleton());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBucketRequired() throws Exception {
        new CouchbaseEndpoint("couchbase:http://localhost:80", "http://localhost:80", new CouchbaseComponent());
    }

    @Test
    public void testDefaultPortIsSet() throws Exception {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint("couchbase:http://localhost/bucket", "http://localhost/bucket", new CouchbaseComponent());
        assertEquals(DEFAULT_COUCHBASE_PORT, endpoint.getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHostnameRequired() throws Exception {
        new CouchbaseEndpoint("couchbase:http://:80/bucket", "couchbase://:80/bucket", new CouchbaseComponent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSchemeRequired() throws Exception {
        new CouchbaseEndpoint("couchbase:localhost:80/bucket", "localhost:80/bucket", new CouchbaseComponent());
    }

    @Test
    public void testCouchbaseEndpoint() {
        new CouchbaseEndpoint();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCouchbaseEndpointWithoutProtocol() throws Exception {
        new CouchbaseEndpoint("localhost:80/bucket", "localhost:80/bucket", new CouchbaseComponent());
    }

    @Test
    public void testCouchbaseEndpointUri() {
        new CouchbaseEndpoint("couchbase:localhost:80/bucket", new CouchbaseComponent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCouchbaseEndpointCreateProducer() throws Exception {
        new CouchbaseEndpoint("couchbase:localhost:80/bucket", new CouchbaseComponent()).createProducer();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCouchbaseEndpointCreateConsumer() throws Exception {
        new CouchbaseEndpoint("couchbase:localhost:80/bucket", new CouchbaseComponent()).createConsumer(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                // Nothing to do
            }
        });
    }

    @Test
    public void testCouchbaseEndpontSettersAndGetters() {
        CouchbaseEndpoint endpoint = new CouchbaseEndpoint();

        endpoint.setProtocol("couchbase");
        assertTrue(endpoint.getProtocol().equals("couchbase"));

        endpoint.setBucket("bucket");
        assertTrue(endpoint.getBucket().equals("bucket"));

        endpoint.setHostname("localhost");
        assertTrue(endpoint.getHostname().equals("localhost"));

        endpoint.setPort(80);
        assertTrue(endpoint.getPort() == 80);

        endpoint.setOperation("PUT");
        assertTrue(endpoint.getOperation().equals("PUT"));

        endpoint.setStartingIdForInsertsFrom(1L);
        assertTrue(endpoint.getStartingIdForInsertsFrom() == 1L);

        endpoint.setProducerRetryAttempts(5);
        assertTrue(endpoint.getProducerRetryAttempts() == 5);

        endpoint.setProducerRetryPause(1);
        assertTrue(endpoint.getProducerRetryPause() == 1);

        endpoint.setDesignDocumentName("beer");
        assertTrue(endpoint.getDesignDocumentName().equals("beer"));

        endpoint.setViewName("brewery_beers");
        assertTrue(endpoint.getViewName().equals("brewery_beers"));

        endpoint.setLimit(1);
        assertTrue(endpoint.getLimit() == 1);

        endpoint.setSkip(1);
        assertTrue(endpoint.getSkip() == 1);

        endpoint.setRangeStartKey("");
        assertTrue(endpoint.getRangeStartKey().equals(""));

        endpoint.setRangeEndKey("");
        assertTrue(endpoint.getRangeEndKey().equals(""));

        endpoint.setConsumerProcessedStrategy("delete");
        assertTrue(endpoint.getConsumerProcessedStrategy().equals("delete"));

        endpoint.setOpTimeOut(1L);
        assertTrue(endpoint.getOpTimeOut() == 1L);

        endpoint.setTimeoutExceptionThreshold(1);
        assertTrue(endpoint.getTimeoutExceptionThreshold() == 1);

        endpoint.setReadBufferSize(1);
        assertTrue(endpoint.getReadBufferSize() == 1);

        endpoint.setShouldOptimize(true);
        assertTrue(endpoint.isShouldOptimize());

        endpoint.setMaxReconnectDelay(1L);
        assertTrue(endpoint.getMaxReconnectDelay() == 1L);

        endpoint.setOpQueueMaxBlockTime(1L);
        assertTrue(endpoint.getOpQueueMaxBlockTime() == 1L);

        endpoint.setObsPollInterval(1L);
        assertTrue(endpoint.getObsPollInterval() == 1L);

        endpoint.setObsTimeout(1L);
        assertTrue(endpoint.getObsTimeout() == 1L);

        endpoint.setDescending(false);
    }
}
