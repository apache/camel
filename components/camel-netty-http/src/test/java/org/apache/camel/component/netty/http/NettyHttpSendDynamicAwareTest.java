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
package org.apache.camel.component.netty.http;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyHttpSendDynamicAwareTest extends BaseNettyTest {

    @Test
    public void testDynamicAware() {
        String out = fluentTemplate.to("direct:moes").withHeader("drink", "beer").request(String.class);
        assertEquals("Drinking beer", out);

        out = fluentTemplate.to("direct:joes").withHeader("drink", "wine").request(String.class);
        assertEquals("Drinking wine", out);

        // and there should only be one http endpoint as they are both on same host
        boolean found = context.getEndpointRegistry()
                .containsKey("netty-http://http://localhost:" + getPort() + "?throwExceptionOnFailure=false");
        assertTrue(found, "Should find static uri");

        // we only have 2xdirect and 2xnetty-http
        assertEquals(4, context.getEndpointRegistry().size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:moes")
                        .toD("netty-http:http://localhost:{{port}}/moes?throwExceptionOnFailure=false&drink=${header.drink}");

                from("direct:joes")
                        .toD("netty-http:http://localhost:{{port}}/joes?throwExceptionOnFailure=false&drink=${header.drink}");

                from("netty-http:http://localhost:{{port}}/?matchOnUriPrefix=true")
                        .transform().simple("Drinking ${header.drink[0]}");
            }
        };
    }

}
