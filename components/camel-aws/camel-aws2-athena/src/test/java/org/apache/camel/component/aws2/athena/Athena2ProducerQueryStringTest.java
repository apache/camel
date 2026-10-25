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
package org.apache.camel.component.aws2.athena;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.athena.model.GetQueryExecutionResponse;
import software.amazon.awssdk.services.athena.model.StartQueryExecutionRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the startQueryExecution operation takes the SQL from: the header, then the queryString option, and the message
 * body only when neither is set.
 */
public class Athena2ProducerQueryStringTest extends CamelTestSupport {

    private static final String ATHENA = "aws2-athena://label?outputLocation=s3://bucket/path/";

    @BindToRegistry("amazonAthenaClient")
    private final AmazonAthenaClientMock client = new AmazonAthenaClientMock();

    @Test
    public void startQueryExecutionRunsQueryStringOptionWhenBodyIsSet() {
        Exchange exchange = template.send(ATHENA + "&queryString=RAW(SELECT 1)",
                e -> e.getIn().setBody("Hello World"));

        assertNull(exchange.getException());
        assertEquals("SELECT 1", lastQuery());
    }

    @Test
    public void startQueryExecutionHeaderOverridesQueryStringOption() {
        Exchange exchange = template.send(ATHENA + "&queryString=RAW(SELECT 1)", e -> {
            e.getIn().setHeader(Athena2Constants.QUERY_STRING, "SELECT 2");
            e.getIn().setBody("Hello World");
        });

        assertNull(exchange.getException());
        assertEquals("SELECT 2", lastQuery());
    }

    @Test
    public void startQueryExecutionHeaderOverridesBody() {
        Exchange exchange = template.send(ATHENA, e -> {
            e.getIn().setHeader(Athena2Constants.QUERY_STRING, "SELECT 2");
            e.getIn().setBody("SELECT 3");
        });

        assertNull(exchange.getException());
        assertEquals("SELECT 2", lastQuery());
    }

    @Test
    public void startQueryExecutionUsesBodyWithoutHeaderAndQueryString() {
        Exchange exchange = template.send(ATHENA, e -> e.getIn().setBody("SELECT 3"));

        assertNull(exchange.getException());
        assertEquals("SELECT 3", lastQuery());
    }

    @Test
    public void startQueryExecutionWithoutQueryFails() {
        Exchange exchange = template.send(ATHENA, e -> e.getIn().setBody(null));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(cause.getMessage().contains(Athena2Constants.QUERY_STRING), cause.getMessage());
        assertNull(client.getLastStartQueryExecutionRequest());
    }

    @Test
    public void startQueryExecutionDoesNotSendResponseOfEarlierStepAsQuery() throws Exception {
        MockEndpoint between = getMockEndpoint("mock:between");
        between.expectedMessageCount(1);
        between.message(0).body().isInstanceOf(GetQueryExecutionResponse.class);

        Exchange exchange = template.send("direct:twoQueries", e -> e.getIn().setBody("Hello World"));

        between.assertIsSatisfied();
        assertNull(exchange.getException());
        assertEquals("SELECT 2", lastQuery());
    }

    @Test
    public void includeTraceSanitizesFromEndpointUri() {
        // the producer template creates the exchange from the endpoint it sends to, so that is the from endpoint
        Exchange exchange = template.send(ATHENA + "&includeTrace=true&accessKey=myAccessKey&secretKey=mySecretKey"
                                          + "&queryString=RAW(SELECT 1)",
                e -> e.getIn().setBody("Hello World"));

        assertNull(exchange.getException());
        String query = lastQuery();
        assertTrue(query.startsWith("-- {\"fromEndpointUri\": \"aws2-athena://label?"), query);
        assertTrue(query.contains("accessKey=xxxxxx"), query);
        assertTrue(query.contains("secretKey=xxxxxx"), query);
        assertFalse(query.contains("myAccessKey"), query);
        assertFalse(query.contains("mySecretKey"), query);
        assertTrue(query.endsWith("\nSELECT 1"), query);
    }

    @Test
    public void includeTraceWithoutFromEndpoint() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader(Athena2Constants.QUERY_STRING, "SELECT 1");

        Exchange out = template.send(ATHENA + "&includeTrace=true", exchange);

        assertNull(out.getException());
        String query = lastQuery();
        assertTrue(query.startsWith("-- {\"exchangeId\": \"" + exchange.getExchangeId() + "\""), query);
        assertTrue(query.endsWith("\nSELECT 1"), query);
    }

    private String lastQuery() {
        StartQueryExecutionRequest request = client.getLastStartQueryExecutionRequest();
        assertNotNull(request, "startQueryExecution was not called");
        return request.queryString();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // the first query waits for its completion, which leaves its response as the message body
                from("direct:twoQueries")
                        .to(ATHENA + "&queryString=RAW(SELECT 1)&waitTimeout=60000&initialDelay=1")
                        .to("mock:between")
                        .to(ATHENA + "&queryString=RAW(SELECT 2)");
            }
        };
    }
}
