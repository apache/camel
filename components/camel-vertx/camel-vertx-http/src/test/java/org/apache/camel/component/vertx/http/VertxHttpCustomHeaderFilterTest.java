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

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class VertxHttpCustomHeaderFilterTest extends VertxHttpTestSupport {

    @BindToRegistry("headerFilterStrategy")
    HeaderFilterStrategy strategy = new UserAgentHeaderFilterStrategy();

    @Test
    public void testCustomHeaderFilterStrategy() {
        Exchange exchange = template.request(getProducerUri() + "?headerFilterStrategy=#headerFilterStrategy", null);
        assertNull(exchange.getMessage().getHeader("User-Agent"));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody(constant("Hello World"));
            }
        };
    }

    private static final class UserAgentHeaderFilterStrategy implements HeaderFilterStrategy {

        @Override
        public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
            return false;
        }

        @Override
        public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
            if (headerName.equalsIgnoreCase("user-agent")) {
                return true;
            }
            return false;
        }
    }
}
