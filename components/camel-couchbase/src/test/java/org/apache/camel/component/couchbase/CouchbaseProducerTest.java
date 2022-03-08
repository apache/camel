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

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.Scope;
import com.couchbase.client.java.kv.MutationResult;
import com.couchbase.client.java.kv.UpsertOptions;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_TTL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CouchbaseProducerTest {

    @Mock
    private Bucket client;

    @Mock
    private Collection collection;

    @Mock
    private Scope scope;

    @Mock
    private CouchbaseEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private Message msg;

    @Mock
    private MutationResult response;
    //    Observable<String> myStringObservable

    @Mock
    private MutationResult of;

    private CouchbaseProducer producer;

    @BeforeEach
    public void before() {
        lenient().when(endpoint.getProducerRetryAttempts()).thenReturn(CouchbaseConstants.DEFAULT_PRODUCER_RETRIES);
        lenient().when(endpoint.getProducerRetryAttempts()).thenReturn(3);
        lenient().when(endpoint.getProducerRetryPause()).thenReturn(200);
        lenient().when(client.defaultCollection()).thenReturn(collection);

        producer = new CouchbaseProducer(endpoint, client, 0, 0);
        lenient().when(exchange.getIn()).thenReturn(msg);
    }

    @Test
    public void testBodyMandatory() {
        assertThrows(CouchbaseException.class,
                () -> producer.process(exchange));
    }

    @Test
    public void testPersistToLowerThanSupported() {
        assertThrows(IllegalArgumentException.class,
                () -> new CouchbaseProducer(endpoint, client, -1, 0));
    }

    @Test
    public void testPersistToHigherThanSupported() {
        assertThrows(IllegalArgumentException.class,
                () -> new CouchbaseProducer(endpoint, client, 5, 0));
    }

    @Test
    public void testReplicateToLowerThanSupported() {
        assertThrows(IllegalArgumentException.class,
                () -> new CouchbaseProducer(endpoint, client, 0, -1));
    }

    @Test
    public void testReplicateToHigherThanSupported() {
        assertThrows(IllegalArgumentException.class,
                () -> new CouchbaseProducer(endpoint, client, 0, 4));
    }

    @Test
    public void testMaximumValuesForPersistToAndReplicateTo() {
        assertDoesNotThrow(() -> producer = new CouchbaseProducer(endpoint, client, 4, 3));
        assertNotNull(producer);
    }

    //
    @Test
    public void testExpiryTimeIsSet() throws Exception {
        // Mock out some headers so we can set an expiry
        int expiry = 5000;
        Map<String, Object> testHeaders = new HashMap<>();
        testHeaders.put("CCB_TTL", Integer.toString(expiry));
        when(msg.getHeaders()).thenReturn(testHeaders);
        when(collection.upsert(anyString(), any(), any())).thenReturn(response);
        when(msg.getHeader(HEADER_TTL, String.class)).thenReturn(Integer.toString(expiry));

        when(endpoint.getId()).thenReturn("123");
        when(endpoint.getOperation()).thenReturn("CCB_PUT");
        when(exchange.getMessage()).thenReturn(msg);
        ArgumentCaptor<UpsertOptions> options = ArgumentCaptor.forClass(UpsertOptions.class);

        producer.process(exchange);

        verify(collection).upsert(anyString(), any(), options.capture());
    }
}
