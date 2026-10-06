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
package org.apache.camel.component.couchdb;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ibm.cloud.cloudant.v1.model.ChangesResult;
import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.util.GsonSingleton;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer must skip the changes that its options filter out, and move past them.
 */
public class CouchDbConsumerFilterTest extends CamelTestSupport {

    // CouchDB sets "deleted" only on the changes of deleted documents
    private static final String UPDATE = "{\"seq\":\"%s\",\"id\":\"%s\",\"changes\":[{\"rev\":\"1-a\"}]}";
    private static final String DELETE = "{\"seq\":\"%s\",\"id\":\"%s\",\"changes\":[{\"rev\":\"2-b\"}],\"deleted\":true}";

    private final CouchDbClientWrapper client = mock(CouchDbClientWrapper.class);
    private final List<Exchange> received = new CopyOnWriteArrayList<>();

    @Test
    void testDeletesFalseMovesPastDeletedDocuments() throws Exception {
        CouchDbConsumer consumer = createConsumer("deletes=false");
        // a whole page of deleted documents, which this consumer does not publish
        Response<ChangesResult> page = changes(DELETE.formatted("1-x", "doc1"), DELETE.formatted("2-x", "doc2"),
                DELETE.formatted("3-x", "doc3"));
        when(client.pollChanges(anyString(), anyString(), anyLong(), anyLong())).thenReturn(page);

        consumer.poll();
        consumer.poll();

        assertEquals(0, received.size());
        // the second poll must ask for the changes after the last one seen, not for the same page again
        verify(client).pollChanges(anyString(), eq("3-x"), anyLong(), anyLong());
    }

    @Test
    void testUpdatesFalseSkipsUpdatedDocuments() throws Exception {
        CouchDbConsumer consumer = createConsumer("updates=false");
        Response<ChangesResult> page = changes(UPDATE.formatted("1-x", "doc1"), DELETE.formatted("2-x", "doc2"));
        when(client.pollChanges(anyString(), anyString(), anyLong(), anyLong())).thenReturn(page);

        consumer.poll();

        assertEquals(1, received.size());
        assertEquals("doc2", received.get(0).getIn().getHeader(CouchDbConstants.HEADER_DOC_ID));
        assertEquals("DELETE", received.get(0).getIn().getHeader(CouchDbConstants.HEADER_METHOD));
    }

    @Test
    void testDefaultPublishesUpdatesAndDeletes() throws Exception {
        CouchDbConsumer consumer = createConsumer("updates=true");
        Response<ChangesResult> page = changes(UPDATE.formatted("1-x", "doc1"), DELETE.formatted("2-x", "doc2"));
        when(client.pollChanges(anyString(), anyString(), anyLong(), anyLong())).thenReturn(page);

        consumer.poll();
        consumer.poll();

        assertEquals(4, received.size());
        assertEquals("UPDATE", received.get(0).getIn().getHeader(CouchDbConstants.HEADER_METHOD));
        assertEquals("DELETE", received.get(1).getIn().getHeader(CouchDbConstants.HEADER_METHOD));
        verify(client).pollChanges(anyString(), eq("2-x"), anyLong(), anyLong());
    }

    private CouchDbConsumer createConsumer(String options) throws Exception {
        when(client.getLatestUpdateSequence()).thenReturn("0");
        CouchDbEndpoint endpoint = context.getEndpoint("couchdb:http://localhost:5984/camel?" + options, CouchDbEndpoint.class);
        CouchDbConsumer consumer = new CouchDbConsumer(endpoint, client, received::add);
        consumer.setMaxMessagesPerPoll(endpoint.getMaxMessagesPerPoll());
        consumer.init();
        return consumer;
    }

    @SuppressWarnings("unchecked")
    private static Response<ChangesResult> changes(String... items) {
        String json = "{\"last_seq\":\"9-x\",\"pending\":0,\"results\":[" + String.join(",", items) + "]}";
        ChangesResult result = GsonSingleton.getGson().fromJson(json, ChangesResult.class);
        Response<ChangesResult> response = mock(Response.class);
        when(response.getResult()).thenReturn(result);
        return response;
    }
}
