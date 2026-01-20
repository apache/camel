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
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NettyHttpRedirectNoLocationTest extends BaseNettyTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port nextPort = AvailablePortFinder.find();

    @Test
    public void testHttpRedirectNoLocation() {
        try {
            template.requestBody("netty-http:http://localhost:" + nextPort + "/test", "Hello World", String.class);
            fail("Should have thrown an exception");
        } catch (RuntimeCamelException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(302, cause.getStatusCode());
            assertTrue(cause.isRedirectError());
            assertFalse(cause.hasRedirectLocation());
            assertNull(cause.getRedirectLocation());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://localhost:" + nextPort + "/test")
                        .process(exchange -> exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 302));
            }
        };
    }
}
