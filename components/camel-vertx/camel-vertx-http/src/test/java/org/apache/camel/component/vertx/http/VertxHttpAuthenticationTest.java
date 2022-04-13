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

import java.util.Base64;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VertxHttpAuthenticationTest extends VertxHttpTestSupport {

    @Test
    public void testBasicAuthentication() {
        String result = template.requestBody(getProducerUri() + "/basic?basicAuthUsername=foo&basicAuthPassword=bar", null,
                String.class);
        Assertions.assertEquals("foo:bar", result);
    }

    @Test
    public void testTokenAuthentication() {
        String result = template.requestBody(getProducerUri() + "/token?bearerToken=ABC123", null, String.class);
        Assertions.assertEquals("ABC123", result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri() + "/basic")
                        .process(exchange -> {
                            // Decode the username & password from the Authorization header
                            String authorization = exchange.getMessage().getHeader("Authorization", String.class);
                            if (authorization != null) {
                                String encoded = authorization.replace("Basic ", "");
                                exchange.getMessage().setBody(Base64.getDecoder().decode(encoded));
                            }
                        });

                from(getTestServerUri() + "/token")
                        .process(exchange -> {
                            // Decode the token from the Authorization header
                            String authorization = exchange.getMessage().getHeader("Authorization", String.class);
                            if (authorization != null) {
                                String token = authorization.replace("Bearer ", "");
                                exchange.getMessage().setBody(token);
                            }
                        });
            }
        };
    }
}
