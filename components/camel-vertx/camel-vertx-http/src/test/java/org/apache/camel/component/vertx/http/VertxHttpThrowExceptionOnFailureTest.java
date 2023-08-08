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
package org.apache.camel.component.vertx.http;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VertxHttpThrowExceptionOnFailureTest extends VertxHttpTestSupport {

    @Test
    public void testThrowExceptionOnFailure() {
        Exchange exchange = template.request(getProducerUri(), null);
        assertTrue(exchange.isFailed());

        Map<String, Object> headers = exchange.getMessage().getHeaders();
        assertTrue(headers.isEmpty());

        HttpOperationFailedException exception = exchange.getException(HttpOperationFailedException.class);
        assertEquals(500, exception.getStatusCode());
        assertEquals("Internal Server Error", exception.getStatusText());
        assertEquals(getTestServerUrl(), exception.getUri());
    }

    @Test
    public void testThrowExceptionOnFailureFromRedirect() {
        Exchange exchange = template.request(getProducerUri() + "/redirect", null);
        assertTrue(exchange.isFailed());

        Map<String, Object> headers = exchange.getMessage().getHeaders();
        assertTrue(headers.isEmpty());

        HttpOperationFailedException exception = exchange.getException(HttpOperationFailedException.class);
        assertEquals(500, exception.getStatusCode());
        assertEquals("Internal Server Error", exception.getStatusText());
        assertEquals(getTestServerUrl() + "/redirect", exception.getUri());
        assertEquals("/", exception.getRedirectLocation());
    }

    @Test
    public void testThrowExceptionOnFailureWithStatusCodeRange() {
        Exchange exchange = template.request(getProducerUri() + "/badstatus?okStatusCodeRange=205-300", null);
        assertTrue(exchange.isFailed());

        Map<String, Object> headers = exchange.getMessage().getHeaders();
        assertTrue(headers.isEmpty());

        HttpOperationFailedException exception = exchange.getException(HttpOperationFailedException.class);
        assertEquals(201, exception.getStatusCode());
        assertEquals("Created", exception.getStatusText());
        assertEquals(getTestServerUrl() + "/badstatus", exception.getUri());
    }

    @Test
    public void testThrowExceptionOnFailureWithStatusCodeRangeSingleValue() {
        Exchange exchange = template.request(getProducerUri() + "/badstatus?okStatusCodeRange=205", null);
        assertTrue(exchange.isFailed());

        Map<String, Object> headers = exchange.getMessage().getHeaders();
        assertTrue(headers.isEmpty());

        HttpOperationFailedException exception = exchange.getException(HttpOperationFailedException.class);
        assertEquals(201, exception.getStatusCode());
        assertEquals("Created", exception.getStatusText());
        assertEquals(getTestServerUrl() + "/badstatus", exception.getUri());
    }

    @Test
    public void testThrowExceptionOnFailureWithOverriddenUri() {
        Exchange exchange = template.request(getProducerUri(), new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getMessage().setHeader(Exchange.HTTP_URI, getTestServerUrl() + "/redirect");
            }
        });
        assertTrue(exchange.isFailed());

        HttpOperationFailedException exception = exchange.getException(HttpOperationFailedException.class);
        assertEquals(500, exception.getStatusCode());
        assertEquals("Internal Server Error", exception.getStatusText());
        assertEquals(getTestServerUrl() + "/redirect", exception.getUri());
    }

    @Test
    public void testThrowExceptionOnFailureFalse() {
        Exchange exchange = template.request(getProducerUri() + "/noexception?throwExceptionOnFailure=false", null);
        assertFalse(exchange.isFailed());

        Map<String, Object> headers = exchange.getMessage().getHeaders();
        assertEquals(500, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Internal Server Error", headers.get(Exchange.HTTP_RESPONSE_TEXT));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .throwException(new Exception("Forced"));

                from(getTestServerUri() + "/badstatus")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(201));

                from(getTestServerUri() + "/noexception")
                        .throwException(new Exception("Forced"));

                from(getTestServerUri() + "/redirect")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(301))
                        .setHeader("Location", constant("/"))
                        .throwException(new Exception("Forced"));
            }
        };
    }
}
