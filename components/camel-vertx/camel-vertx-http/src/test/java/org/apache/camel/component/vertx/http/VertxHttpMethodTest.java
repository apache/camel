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

import io.vertx.core.http.HttpMethod;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpMethodTest extends VertxHttpTestSupport {

    @Test
    public void testDefaultMethodGet() {
        String result = template.requestBody(getProducerUri(), null, String.class);
        assertEquals(HttpMethod.GET.name(), result);
    }

    @Test
    public void testMethodSetFromEndpoint() {
        String result = template.requestBody(getProducerUri() + "?httpMethod=DELETE", null, String.class);
        assertEquals(HttpMethod.DELETE.name(), result);
    }

    @Test
    public void testMethodSetFromHeader() {
        String result = template.requestBodyAndHeader(getProducerUri(), null, Exchange.HTTP_METHOD, HttpMethod.PUT.name(),
                String.class);
        assertEquals(HttpMethod.PUT.name(), result);
    }

    @Test
    public void testDefaultMethodGetWhenQueryStringProvided() {
        String result = template.requestBody(getProducerUri() + "/?foo=bar&cheese=wine", null, String.class);
        assertEquals(HttpMethod.GET.name(), result);
    }

    @Test
    public void testDefaultMethodGetWhenQueryStringProvidedFromHeader() {
        String result = template.requestBodyAndHeader(getProducerUri(), null, Exchange.HTTP_QUERY, "foo=bar&cheese=wine",
                String.class);
        assertEquals(HttpMethod.GET.name(), result);
    }

    @Test
    public void testDefaultMethodPostWhenBodyNotNull() {
        String result = template.requestBody(getProducerUri(), "Test Body", String.class);
        assertEquals(HttpMethod.POST.name(), result);
    }

    @Test
    public void testEndpointConfigurationPrecedence() {
        String result = template.requestBody(getProducerUri() + "?httpMethod=DELETE&foo=bar", null, String.class);
        assertEquals(HttpMethod.DELETE.name(), result);
    }

    @Test
    public void testHeaderConfigurationPrecedence() {
        String result = template.requestBodyAndHeader(getProducerUri() + "?foo=bar", null, Exchange.HTTP_METHOD,
                HttpMethod.PUT.name(), String.class);
        assertEquals(HttpMethod.PUT.name(), result);
    }

    @Test
    public void testQueryStringPrecedence() {
        String result = template.requestBody(getProducerUri() + "/?foo=bar&cheese=wine", "Test Body", String.class);
        assertEquals(HttpMethod.GET.name(), result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody(header(Exchange.HTTP_METHOD));
            }
        };
    }
}
