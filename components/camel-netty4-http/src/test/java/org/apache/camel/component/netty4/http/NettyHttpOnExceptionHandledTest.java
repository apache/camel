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

public class NettyHttpOnExceptionHandledTest extends BaseNettyTest {

    @Test
    public void testOnExceptionHandled() throws Exception {
        Exchange reply = template.request("netty4-http:http://localhost:{{port}}/myserver?throwExceptionOnFailure=false", null);

        assertNotNull(reply);
        assertEquals("Dude something went wrong", reply.getOut().getBody(String.class));
        assertEquals(500, reply.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("netty4-http:http://localhost:{{port}}/myserver")
                    // use onException to catch all exceptions and return a custom reply message
                    .onException(Exception.class)
                        .handled(true)
                        // create a custom failure response
                        .transform(constant("Dude something went wrong"))
                        // we must remember to set error code 500 as handled(true)
                        // otherwise would let Camel thing its a OK response (200)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                    .end()
                    // now just force an exception immediately
                    .throwException(new IllegalArgumentException("I cannot do this"));
                // END SNIPPET: e1
            }
        };
    }
}
