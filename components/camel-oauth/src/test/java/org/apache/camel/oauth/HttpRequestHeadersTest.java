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
package org.apache.camel.oauth;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A message that gives no access to the HTTP request: the header values are read from the message, without the values
 * that also appear in the query string of the request.
 */
class HttpRequestHeadersTest {

    @Test
    void messageHeaderIsReadWhenThereIsNoQueryString() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Message message = new DefaultExchange(context).getMessage();
            message.setHeader("Authorization", "Bearer token");

            assertEquals(List.of("Bearer token"), HttpRequestHeaders.values(message, "Authorization"));
        }
    }

    @Test
    void messageHeaderIsReadWhenTheQueryStringDoesNotHoldIt() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Message message = new DefaultExchange(context).getMessage();
            message.setHeader("Authorization", "Bearer token");
            message.setHeader(Exchange.HTTP_RAW_QUERY, "page=2&sort=name");

            assertEquals(List.of("Bearer token"), HttpRequestHeaders.values(message, "Authorization"));
        }
    }

    @Test
    void valueThatTheQueryStringHoldsIsLeftOut() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Message message = new DefaultExchange(context).getMessage();
            message.setHeader("Authorization", "Bearer token");
            message.setHeader(Exchange.HTTP_RAW_QUERY, "Authorization=Bearer%20token");

            assertEquals(List.of(), HttpRequestHeaders.values(message, "Authorization"));
        }
    }

    @Test
    void valueThatTheUndecodedQueryStringHoldsIsLeftOut() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Message message = new DefaultExchange(context).getMessage();
            message.setHeader("Cookie", "camel.oauth.session=abc");
            message.setHeader(Exchange.HTTP_QUERY, "Cookie=camel.oauth.session=abc");

            assertEquals(List.of(), HttpRequestHeaders.values(message, "Cookie"));
        }
    }

    @Test
    void onlyTheValueThatTheQueryStringHoldsIsLeftOut() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Message message = new DefaultExchange(context).getMessage();
            message.setHeader("Authorization", List.of("Bearer header-token", "Bearer query-token"));
            message.setHeader(Exchange.HTTP_RAW_QUERY, "Authorization=Bearer%20query-token");

            assertEquals(List.of("Bearer header-token"), HttpRequestHeaders.values(message, "Authorization"));
        }
    }

    @Test
    void noValueIsReadWhenTheQueryStringCannotBeDecoded() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Message message = new DefaultExchange(context).getMessage();
            message.setHeader("Authorization", "Bearer token");
            message.setHeader(Exchange.HTTP_RAW_QUERY, "discount=100%");

            assertEquals(List.of(), HttpRequestHeaders.values(message, "Authorization"));
        }
    }
}
