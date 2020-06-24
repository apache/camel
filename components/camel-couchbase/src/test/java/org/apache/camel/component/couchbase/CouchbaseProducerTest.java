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

import com.couchbase.client.CouchbaseClient;
import net.spy.memcached.internal.OperationFuture;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.apache.camel.component.couchbase.CouchbaseConstants.HEADER_TTL;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CouchbaseProducerTest {

    @Mock
    private CouchbaseClient client;

    @Mock
    private CouchbaseEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private Message msg;

    @Mock
    private OperationFuture<?> response;

    @Mock
    private OperationFuture<Boolean> of;

    private CouchbaseProducer producer;

    @BeforeEach
    public void before() throws Exception {
        lenient().when(endpoint.getProducerRetryAttempts()).thenReturn(CouchbaseConstants.DEFAULT_PRODUCER_RETRIES);
        producer = new CouchbaseProducer(endpoint, client, 0, 0);
        lenient().when(exchange.getIn()).thenReturn(msg);
    }

    @Test
    public void testBodyMandatory() throws Exception {
        assertThrows(CouchbaseException.class,
            () -> producer.process(exchange));
    }

    @Test
    public void testPersistToLowerThanSupported() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new CouchbaseProducer(endpoint, client, -1, 0));
    }

    @Test
    public void testPersistToHigherThanSupported() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new CouchbaseProducer(endpoint, client, 5, 0));
    }

    @Test
    public void testReplicateToLowerThanSupported() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new CouchbaseProducer(endpoint, client, 0, -1));
    }

    @Test
    public void testReplicateToHigherThanSupported() throws Exception {
        assertThrows(IllegalArgumentException.class,
            () -> new CouchbaseProducer(endpoint, client, 0, 4));
    }

    @Test
    public void testMaximumValuesForPersistToAndRepicateTo() throws Exception {
        producer = new CouchbaseProducer(endpoint, client, 4, 3);
    }

    @Test
    public void testExpiryTimeIsSet() throws Exception {
        when(of.get()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Exception {
                return true;

            }
        });

        when(client.set(anyString(), anyInt(), any(), any(), any())).thenReturn(of);

        // Mock out some headers so we can set an expiry
        int expiry = 5000;
        Map<String, Object> testHeaders = new HashMap<>();
        testHeaders.put("CCB_TTL", Integer.toString(expiry));
        when(msg.getHeaders()).thenReturn(testHeaders);
        when(msg.getHeader(HEADER_TTL, String.class)).thenReturn(Integer.toString(expiry));

        when(endpoint.getId()).thenReturn("123");
        when(endpoint.getOperation()).thenReturn("CCB_PUT");
        when(exchange.getOut()).thenReturn(msg);

        producer.process(exchange);

        verify(client).set(anyString(), eq(expiry), any(), any(), any());
    }

    @Test
    public void testTimeOutRetryToException() throws Exception {

        when(of.get()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Exception {
                throw new RuntimeException("Timed out waiting for operation");

            }
        });

        when(client.set(anyString(), anyInt(), any(), any(), any())).thenReturn(of);
        when(endpoint.getId()).thenReturn("123");
        when(endpoint.getOperation()).thenReturn("CCB_PUT");
        try {
            producer.process(exchange);
        } catch (Exception e) {
            // do nothing
            verify(of, times(3)).get();
        }

    }

    @Test
    public void testTimeOutRetryThenSuccess() throws Exception {

        when(of.get()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Exception {
                throw new RuntimeException("Timed out waiting for operation");
            }
        }).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Exception {
                return true;
            }
        });

        when(client.set(anyString(), anyInt(), any(), any(), any())).thenReturn(of);
        when(endpoint.getId()).thenReturn("123");
        when(endpoint.getOperation()).thenReturn("CCB_PUT");
        when(exchange.getOut()).thenReturn(msg);

        producer.process(exchange);

        verify(of, times(2)).get();
        verify(msg).setBody(true);
    }

    @Test
    public void testTimeOutRetryTwiceThenSuccess() throws Exception {

        when(of.get()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Exception {
                throw new RuntimeException("Timed out waiting for operation");
            }
        }).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Exception {
                throw new RuntimeException("Timed out waiting for operation");
            }
        }).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Exception {
                return true;
            }
        });

        when(client.set(anyString(), anyInt(), any(), any(), any())).thenReturn(of);
        when(endpoint.getId()).thenReturn("123");
        when(endpoint.getOperation()).thenReturn("CCB_PUT");
        when(exchange.getOut()).thenReturn(msg);

        producer.process(exchange);

        verify(of, times(3)).get();
        verify(msg).setBody(true);
    }
}
