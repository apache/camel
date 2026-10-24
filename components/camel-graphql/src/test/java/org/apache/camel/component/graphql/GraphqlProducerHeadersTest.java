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
package org.apache.camel.component.graphql;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.graphql.server.GraphqlServer;
import org.apache.camel.component.http.HttpConstants;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The headers of the producer for the HTTP request, and the headers of the message after the call.
 */
public class GraphqlProducerHeadersTest extends CamelTestSupport {

    private static final String BOOK_BY_ID_QUERY
            = "query BookById($id: ID) { bookById(id: $id) { id name author { name } } }";

    private static String booksQueryResult;
    private static String bookByIdQueryResult;
    private static GraphqlServer server;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        booksQueryResult = readJsonFile("booksQueryResult.json");
        bookByIdQueryResult = readJsonFile("bookByIdQueryResult.json");

        server = new GraphqlServer();
        server.start();
    }

    private static String readJsonFile(String name) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(objectMapper.readValue(
                IOHelper.loadText(ObjectHelper.loadResourceAsStream(name)), Object.class));
    }

    @AfterAll
    public static void tearDownAfterClass() {
        server.shutdown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:static")
                        .to("graphql://http://localhost:" + server.getPort() + "/graphql?query={books{id name}}");
                from("direct:headers")
                        .to("graphql://http://localhost:" + server.getPort()
                            + "/graphql?queryHeader=myQuery&variablesHeader=myVariables");
                from("direct:body")
                        .to("graphql://http://localhost:" + server.getPort() + "/graphql");
                from("direct:variablesBody")
                        .to("graphql://http://localhost:" + server.getPort()
                            + "/graphql?queryFile=multipleQueries.graphql&operationName=BookById");
            }
        };
    }

    @Test
    public void requestHeadersOfProducerWinOverMessageHeaders() {
        Exchange exchange = template.request("direct:static", e -> {
            e.getIn().setHeader("Content-Type", "text/plain");
            e.getIn().setHeader("Accept", "text/html");
            e.getIn().setHeader("Content-Encoding", "gzip");
        });

        assertNull(exchange.getException());
        assertEquals(booksQueryResult, exchange.getMessage().getBody(String.class));

        Map<String, String> sent = server.getLastRequestHeaders();
        assertEquals("application/json", sent.get("Content-Type"));
        assertEquals("application/json", sent.get("Accept"));
        assertFalse(sent.containsKey("Content-Encoding"));
    }

    @Test
    public void queryAndVariablesHeadersAreNotSentAsHttpHeaders() {
        JsonObject variables = new JsonObject();
        variables.put("id", "book-1");

        Exchange exchange = template.request("direct:headers", e -> {
            e.getIn().setHeader("myQuery", BOOK_BY_ID_QUERY);
            e.getIn().setHeader("myVariables", variables);
        });

        assertNull(exchange.getException());
        assertEquals(bookByIdQueryResult, exchange.getMessage().getBody(String.class));

        Map<String, String> sent = server.getLastRequestHeaders();
        assertFalse(sent.containsKey("myQuery"));
        assertFalse(sent.containsKey("myVariables"));

        // the headers stay on the message
        assertEquals(BOOK_BY_ID_QUERY, exchange.getMessage().getHeader("myQuery"));
        assertNotNull(exchange.getMessage().getHeader("myVariables"));
    }

    @Test
    public void requestHeadersOfProducerDoNotStayOnMessage() {
        Exchange exchange = template.request("direct:static", e -> e.getIn().setBody(""));

        assertNull(exchange.getException());
        Message message = exchange.getMessage();
        assertNull(message.getHeader(HttpConstants.HTTP_METHOD));
        assertNull(message.getHeader("Accept"));
        assertNull(message.getHeader("Accept-Encoding"));
        // the Content-Type of the response of the server
        assertEquals("application/json; charset=UTF-8", message.getHeader("Content-Type"));
        assertEquals(200, message.getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    public void requestHeadersOfMessageAreKeptAfterCall() {
        Exchange exchange = template.request("direct:static", e -> {
            e.getIn().setHeader("Accept", "text/html");
            e.getIn().setHeader("Accept-Encoding", "identity");
        });

        assertNull(exchange.getException());
        Message message = exchange.getMessage();
        assertEquals("text/html", message.getHeader("Accept"));
        assertEquals("identity", message.getHeader("Accept-Encoding"));
        assertNull(message.getHeader(HttpConstants.HTTP_METHOD));
    }

    @Test
    public void mapBodyIsNotSentAsQuery() {
        Exchange exchange = template.request("direct:body", e -> e.getIn().setBody(Map.of("id", "1")));

        InvalidPayloadException e = assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertTrue(e.getMessage().contains("queryHeader"), e.getMessage());
    }

    @Test
    public void mapBodyIsUsedAsVariables() {
        Exchange exchange = template.request("direct:variablesBody", e -> e.getIn().setBody(Map.of("id", "book-1")));

        assertNull(exchange.getException());
        assertEquals(bookByIdQueryResult, exchange.getMessage().getBody(String.class));
    }

}
