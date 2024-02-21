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

public class VertxHttpUriTest extends VertxHttpTestSupport {

    @Test
    public void testHttpUriFromHeader() {
        String result = template.requestBodyAndHeader(getProducerUri(), null, Exchange.HTTP_URI,
                getTestServerUrl() + "/alternate", String.class);
        assertEquals("Overridden URI", result);
    }

    @Test
    public void testHttpUriAndPathFromHeader() {
        String result = fluentTemplate.to(getProducerUri())
                .withHeader(Exchange.HTTP_URI, getTestServerUrl() + "/alternate")
                .withHeader(Exchange.HTTP_PATH, "/with/path")
                .request(String.class);
        assertEquals("Overridden URI + path", result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody(constant("Hello World"));

                from(getTestServerUri() + "/alternate")
                        .setBody(constant("Overridden URI"));

                from(getTestServerUri() + "/alternate/with/path")
                        .setBody(constant("Overridden URI + path"));

            }
        };
    }
}
