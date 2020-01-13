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
package org.apache.camel.component.cmis;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.junit.Before;
import org.junit.Test;

public class CMISQueryProducerTest extends CMISTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        populateServerWithContent();
    }

    @Test
    public void queryServerForDocumentWithSpecificName() throws Exception {
        Endpoint endpoint = context.getEndpoint("cmis://" + getUrl() + "?queryMode=true");
        Producer producer = endpoint.createProducer();

        Exchange exchange = createExchangeWithInBody(
                "SELECT * FROM cmis:document WHERE cmis:name = 'test1.txt'");
        producer.process(exchange);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = exchange.getMessage().getBody(List.class);
        assertEquals(1, documents.size());
        assertEquals("test1.txt", documents.get(0).get("cmis:name"));
    }

    @Test
    public void getResultCountFromHeader() throws Exception {
        Endpoint endpoint = context.getEndpoint("cmis://" + getUrl() + "?queryMode=true");
        Producer producer = endpoint.createProducer();

        Exchange exchange = createExchangeWithInBody(
                "SELECT * FROM cmis:document WHERE CONTAINS('Camel test content.')");
        producer.process(exchange);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = exchange.getMessage().getBody(List.class);
        assertEquals(2, documents.size());
        assertEquals(2, exchange.getMessage().getHeader("CamelCMISResultCount"));
    }

    @Test
    public void limitNumberOfResultsWithReadSizeHeader() throws Exception {
        Endpoint endpoint = context.getEndpoint("cmis://" + getUrl() + "?queryMode=true");
        Producer producer = endpoint.createProducer();

        Exchange exchange = createExchangeWithInBody(
                "SELECT * FROM cmis:document WHERE CONTAINS('Camel test content.')");
        exchange.getIn().getHeaders().put("CamelCMISReadSize", 1);

        producer.process(exchange);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = exchange.getMessage().getBody(List.class);
        assertEquals(1, documents.size());
    }

    @Test
    public void retrieveAlsoDocumentContent() throws Exception {
        Endpoint endpoint = context.getEndpoint("cmis://" + getUrl() + "?queryMode=true");
        Producer producer = endpoint.createProducer();

        Exchange exchange = createExchangeWithInBody(
                "SELECT * FROM cmis:document WHERE cmis:name='test1.txt'");
        exchange.getIn().getHeaders().put("CamelCMISRetrieveContent", true);

        producer.process(exchange);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> documents = exchange.getMessage().getBody(List.class);
        InputStream content = (InputStream)documents.get(0).get("CamelCMISContent");
        assertEquals("This is the first Camel test content.", readFromStream(content));
    }

    private void populateServerWithContent() throws UnsupportedEncodingException {
        Folder newFolder = createFolderWithName("CamelCmisTestFolder");
        createTextDocument(newFolder, "This is the first Camel test content.", "test1.txt");
        createTextDocument(newFolder, "This is the second Camel test content.", "test2.txt");
    }

}
