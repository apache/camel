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

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpRedirectNoLocationTest extends BaseNettyTest {

    private int nextPort;

    @Test
    public void testHttpRedirectNoLocation() throws Exception {
        try {
            template.requestBody("netty-http:http://localhost:" + nextPort + "/test", "Hello World", String.class);
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(302, cause.getStatusCode());
            assertEquals(true, cause.isRedirectError());
            assertEquals(false, cause.hasRedirectLocation());
            assertEquals(null, cause.getRedirectLocation());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                nextPort = getNextPort();

                from("netty-http:http://localhost:" + nextPort + "/test")
                        .process(exchange -> exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 302));
            }
        };
    }
}
