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

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpQueryStringTest extends VertxHttpTestSupport {

    @Test
    public void testQueryStringPropertyPlaceholder() {
        String result = template.requestBody(getProducerUri() + "?os={{sys:os.name}}", null, String.class);
        assertEquals("os=" + System.getProperty("os.name"), result.replaceAll("%20", " "));
    }

    @Test
    public void testQueryStringFromHttpUriHeader() {
        String result = template.requestBody(getProducerUri() + "?os={{sys:os.name}}", null, String.class);
        assertEquals("os=" + System.getProperty("os.name"), result.replaceAll("%20", " "));
    }

    @Test
    public void testQueryStringUnsafeCharacters() {
        String result = template.requestBody(getProducerUri() + "?foo=bar#^[]", null, String.class);
        assertEquals("foo=bar%23%5E%5B%5D", result);
    }

    @Test
    public void testQueryStringUnsafeCharactersFromHttpQueryHeader() {
        String result = template.requestBodyAndHeader(getProducerUri(), null, Exchange.HTTP_QUERY, "foo=bar#^[]", String.class);
        assertEquals("foo=bar%23%5E%5B%5D", result);
    }

    @Test
    public void testQueryStringUnsafeCharactersFromHttpUriHeader() {
        String result = template.requestBody("direct:start", null, String.class);
        assertEquals("foo=bar%23%5E%5B%5D", result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody(header(Exchange.HTTP_QUERY));

                from("direct:start")
                        .setHeader(Exchange.HTTP_URI, constant(getTestServerUrl() + "?foo=bar#^[]"))
                        .to(getProducerUri());
            }
        };
    }
}
