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
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class NettyHttpProducerThrowExceptionOnFailureTest extends BaseNettyTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpProducerThrowExceptionOnFailureTest.class);

    @Test
    public void testFailWithoutException() {
        try {
            String out = template().requestBody("netty-http:http://localhost:{{port}}/fail?throwExceptionOnFailure=false", null,
                    String.class);
            assertEquals("Fail", out);
        } catch (Throwable t) {
            LOG.error("Unexpected exception: {}", t.getMessage(), t);
            fail("Should not throw an exception");
        }
    }

    @Test
    public void testFailWithException() {
        try {
            template().requestBody("netty-http:http://localhost:{{port}}/fail?throwExceptionOnFailure=true", null,
                    String.class);
            fail("Should throw an exception");
        } catch (Throwable t) {
            NettyHttpOperationFailedException cause = (NettyHttpOperationFailedException) t.getCause();
            assertEquals(404, cause.getStatusCode());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://localhost:{{port}}/fail")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE).constant(404)
                        .transform(constant("Fail"));
            }
        };
    }
}
