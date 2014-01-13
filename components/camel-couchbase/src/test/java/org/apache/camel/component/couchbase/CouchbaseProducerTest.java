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

import com.couchbase.client.CouchbaseClient;
import net.spy.memcached.internal.OperationFuture;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

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
    private OperationFuture response;

    private CouchbaseProducer producer;

    @Before
    public void before() {
        initMocks(this);
        producer = new CouchbaseProducer(endpoint, client);
        when(exchange.getIn()).thenReturn(msg);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = CouchbaseException.class)
    public void testBodyMandatory() throws Exception {
        when(msg.getMandatoryBody()).thenThrow(InvalidPayloadException.class);
        producer.process(exchange);
    }


    @Test
    public void testDocumentHeadersAreSet() throws Exception {

        String doc = "ugol";
        when(msg.getMandatoryBody()).thenReturn(doc);
        when(client.set("1", doc).get()).thenReturn(true);

        producer.process(exchange);
        verify(msg).setHeader(CouchbaseConstants.HEADER_ID, "1");
    }
/*
    @SuppressWarnings("unchecked")
    @Test(expected = InvalidPayloadException.class)
    public void testNullSaveResponseThrowsError() throws Exception {
        when(exchange.getIn().getMandatoryBody()).thenThrow(InvalidPayloadException.class);
        when(producer.getBodyAsJsonElement(exchange)).thenThrow(InvalidPayloadException.class);
        producer.process(exchange);
    }
*/

}
