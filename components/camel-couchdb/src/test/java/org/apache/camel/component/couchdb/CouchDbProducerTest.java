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

import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lightcouch.Response;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CouchDbProducerTest {

    @Mock
    private CouchDbClientWrapper client;

    @Mock
    private CouchDbEndpoint endpoint;

    @Mock
    private Exchange exchange;

    @Mock
    private Message msg;

    @Mock
    private Response response;

    private CouchDbProducer producer;

    @Before
    public void before() {
        producer = new CouchDbProducer(endpoint, client);
        when(exchange.getIn()).thenReturn(msg);
    }

    @Test(expected = InvalidPayloadException.class)
    public void testBodyMandatory() throws Exception {
        when(msg.getMandatoryBody()).thenThrow(InvalidPayloadException.class);
        producer.process(exchange);
    }

    @Test
    public void testDocumentHeadersAreSet() throws Exception {
        String id = UUID.randomUUID().toString();
        String rev = UUID.randomUUID().toString();

        JsonObject doc = new JsonObject();
        doc.addProperty("_id", id);
        doc.addProperty("_rev", rev);

        when(msg.getMandatoryBody()).thenReturn(doc);
        when(client.update(doc)).thenReturn(response);
        when(response.getId()).thenReturn(id);
        when(response.getRev()).thenReturn(rev);

        producer.process(exchange);
        verify(msg).setHeader(CouchDbConstants.HEADER_DOC_ID, id);
        verify(msg).setHeader(CouchDbConstants.HEADER_DOC_REV, rev);
    }

    @Test(expected = InvalidPayloadException.class)
    public void testNullSaveResponseThrowsError() throws Exception {
        when(exchange.getIn().getMandatoryBody()).thenThrow(InvalidPayloadException.class);
        when(producer.getBodyAsJsonElement(exchange)).thenThrow(InvalidPayloadException.class);
        producer.process(exchange);
    }
    
    @Test
    public void testDeleteResponse() throws Exception {
        String id = UUID.randomUUID().toString();
        String rev = UUID.randomUUID().toString();

        JsonObject doc = new JsonObject();
        doc.addProperty("_id", id);
        doc.addProperty("_rev", rev);

        when(msg.getHeader(CouchDbConstants.HEADER_METHOD, String.class)).thenReturn("DELETE");
        when(msg.getMandatoryBody()).thenReturn(doc);
        when(client.remove(doc)).thenReturn(response);
        when(response.getId()).thenReturn(id);
        when(response.getRev()).thenReturn(rev);

        producer.process(exchange);
        verify(msg).setHeader(CouchDbConstants.HEADER_DOC_ID, id);
        verify(msg).setHeader(CouchDbConstants.HEADER_DOC_REV, rev);
    }
    
    @Test
    public void testGetResponse() throws Exception {
        String id = UUID.randomUUID().toString();

        JsonObject doc = new JsonObject();
        doc.addProperty("_id", id);

        when(msg.getHeader(CouchDbConstants.HEADER_METHOD, String.class)).thenReturn("GET");
        when(msg.getHeader(CouchDbConstants.HEADER_DOC_ID, String.class)).thenReturn(id);
        when(msg.getMandatoryBody()).thenReturn(doc);
        when(client.get(id)).thenReturn(response);

        producer.process(exchange);
        verify(msg).getHeader(CouchDbConstants.HEADER_DOC_ID, String.class);
    }

    @Test
    public void testStringBodyIsConvertedToJsonTree() throws Exception {
        when(msg.getMandatoryBody()).thenReturn("{ \"name\" : \"coldplay\" }");
        when(client.save(any())).thenAnswer(new Answer<Response>() {

            @Override
            public Response answer(InvocationOnMock invocation) throws Throwable {
                assertTrue(invocation.getArguments()[0].getClass() + " but wanted " + JsonElement.class,
                        invocation.getArguments()[0] instanceof JsonElement);
                return new Response();
            }
        });
        producer.process(exchange);
        verify(client).save(any(JsonObject.class));
    }
}
