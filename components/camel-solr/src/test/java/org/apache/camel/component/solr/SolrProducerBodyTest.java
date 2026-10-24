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
package org.apache.camel.component.solr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.SolrPing;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the requests the producer builds from the message body and headers, without a Solr server.
 */
public class SolrProducerBodyTest extends CamelTestSupport {

    private static final String URI = "solr://localhost:8983/solr/mycollection?solrClient=#capturingClient";

    @BindToRegistry("capturingClient")
    private final CapturingSolrClient client = new CapturingSolrClient();

    @BeforeEach
    public void clearRequests() {
        client.requests.clear();
    }

    @Test
    public void searchWithoutBodyFails() {
        Exchange exchange = send(URI, null, Map.of(SolrConstants.PARAM_OPERATION, "SEARCH"));
        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertTrue(client.requests.isEmpty());
    }

    @Test
    public void searchWithoutBodyUsesQueryStringHeader() {
        Exchange exchange = send(URI, null, Map.of(SolrConstants.PARAM_QUERY_STRING, "id:1"));
        assertNull(exchange.getException());
        QueryRequest request = assertInstanceOf(QueryRequest.class, client.requests.get(0));
        assertEquals("id:1", request.getParams().get("q"));
    }

    @Test
    public void deleteWithoutBodyFails() {
        Exchange exchange = send(URI, null, Map.of(SolrConstants.PARAM_OPERATION, "DELETE"));
        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertTrue(client.requests.isEmpty());
    }

    @Test
    public void deleteWithoutBodyAndWithCommitSendsOnlyTheCommit() {
        Exchange exchange = send(URI, null,
                Map.of(SolrConstants.PARAM_OPERATION, "DELETE", SolrConstants.HEADER_PARAM_PREFIX + "commit", "true"));
        assertNull(exchange.getException());
        UpdateRequest request = assertInstanceOf(UpdateRequest.class, client.requests.get(0));
        assertNull(request.getDeleteById());
        assertNull(request.getDeleteQuery());
        assertEquals("true", request.getParams().get("commit"));
    }

    @Test
    public void pingWithoutBody() {
        Exchange exchange = send(URI, null, Map.of(SolrConstants.PARAM_OPERATION, "PING"));
        assertNull(exchange.getException());
        assertInstanceOf(SolrPing.class, client.requests.get(0));
    }

    @Test
    public void insertWithoutBodyUsesFieldHeaders() {
        Exchange exchange = send(URI, null,
                Map.of(SolrConstants.PARAM_OPERATION, "INSERT", SolrConstants.HEADER_FIELD_PREFIX + "id", "1"));
        assertNull(exchange.getException());
        UpdateRequest request = assertInstanceOf(UpdateRequest.class, client.requests.get(0));
        assertEquals("1", request.getDocuments().get(0).getFieldValue("id"));
    }

    @Test
    public void insertWithoutBodyAndWithCommitSendsOnlyTheCommit() {
        Exchange exchange = send(URI + "&autoCommit=true", null, Map.of(SolrConstants.PARAM_OPERATION, "INSERT"));
        assertNull(exchange.getException());
        UpdateRequest request = assertInstanceOf(UpdateRequest.class, client.requests.get(0));
        assertNull(request.getDocuments());
        assertEquals("true", request.getParams().get("commit"));
    }

    @Test
    public void insertWithoutBodyAndWithoutParametersFails() {
        Exchange exchange = send(URI, null, Map.of(SolrConstants.PARAM_OPERATION, "INSERT"));
        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertTrue(client.requests.isEmpty());
    }

    @Test
    public void insertOfBodyThatIsNotADocumentWithCommitFails() {
        List<Object> bodies = List.of(
                "plain text", "plain text".getBytes(StandardCharsets.UTF_8),
                new ByteArrayInputStream(new byte[] { 1, 2 }), 42, new NotADocument());
        for (Object body : bodies) {
            Exchange exchange = send(URI + "&autoCommit=true", body, Map.of(SolrConstants.PARAM_OPERATION, "INSERT"));
            assertInstanceOf(InvalidPayloadException.class, exchange.getException(), "body: " + body);
            exchange = send(URI, body,
                    Map.of(SolrConstants.PARAM_OPERATION, "INSERT", SolrConstants.HEADER_PARAM_PREFIX + "commit", "true"));
            assertInstanceOf(InvalidPayloadException.class, exchange.getException(), "body: " + body);
        }
        assertTrue(client.requests.isEmpty());
    }

    @Test
    public void insertOfXmlWithDeclarationIsWellFormed() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><doc><field name=\"id\">1</field></doc>";
        Exchange exchange = send(URI, xml, Map.of(SolrConstants.PARAM_OPERATION, "INSERT"));
        assertNull(exchange.getException());
        ContentStreamUpdateRequest request = assertInstanceOf(ContentStreamUpdateRequest.class, client.requests.get(0));
        ContentStream stream = request.getContentStreams().iterator().next();
        try (InputStream is = stream.getStream()) {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
            assertEquals("add", document.getDocumentElement().getNodeName());
            assertEquals("doc", document.getDocumentElement().getFirstChild().getNodeName());
        }
    }

    @Test
    public void insertOfMapWithNullValue() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "1");
        body.put("name", null);
        Map<String, Object> headers = new HashMap<>();
        headers.put(SolrConstants.PARAM_OPERATION, "INSERT");
        headers.put(SolrConstants.HEADER_FIELD_PREFIX + "manu", null);
        Exchange exchange = send(URI, body, headers);
        assertNull(exchange.getException());
        UpdateRequest request = assertInstanceOf(UpdateRequest.class, client.requests.get(0));
        SolrInputDocument document = request.getDocuments().get(0);
        assertEquals("1", document.getFieldValue("id"));
        assertNull(document.getFieldValue("name"));
        assertNull(document.getFieldValue("manu"));
    }

    private Exchange send(String uri, Object body, Map<String, Object> headers) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(body);
        exchange.getMessage().setHeaders(new HashMap<>(headers));
        return template.send(uri, exchange);
    }

    public static class NotADocument {
        public String getName() {
            return "camel";
        }
    }

    private static class CapturingSolrClient extends SolrClient {
        private final List<SolrRequest<?>> requests = new ArrayList<>();

        @Override
        public NamedList<Object> request(SolrRequest<?> request, String collection) {
            requests.add(request);
            NamedList<Object> response = new NamedList<>();
            response.add("status", 0);
            return response;
        }

        @Override
        public void close() throws IOException {
            // noop
        }
    }
}
