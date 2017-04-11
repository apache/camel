/**
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
package org.apache.camel.component.netty4.http;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class NettyHttpProducerThrowExceptionOnFailureTest extends BaseNettyTest {

    @Test
    public void testFailWithoutException() throws Exception {
        try {
            String out = template().requestBody("netty4-http:http://localhost:{{port}}/fail?throwExceptionOnFailure=false", null, String.class);
            assertEquals("Fail", out);
        } catch (Throwable t) {
            t.printStackTrace();
            fail("Should not throw an exception");
        }
    }

    @Test
    public void testFailWithException() throws Exception {
        try {
            String out = template().requestBody("netty4-http:http://localhost:{{port}}/fail?throwExceptionOnFailure=true", null, String.class);
            fail("Should throw an exception");
        } catch (Throwable t) {
            NettyHttpOperationFailedException cause = (NettyHttpOperationFailedException) t.getCause();
            assertEquals(404, cause.getStatusCode());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://localhost:{{port}}/fail")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE).constant(404)
                        .transform(constant("Fail"));
            }
        };
    }
}
