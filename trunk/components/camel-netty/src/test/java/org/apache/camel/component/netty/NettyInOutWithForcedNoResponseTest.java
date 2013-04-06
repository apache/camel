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
package org.apache.camel.component.netty;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class NettyInOutWithForcedNoResponseTest extends BaseNettyTest {

    @Test
    public void testResponse() throws Exception {
        Object out = template.requestBody("netty:tcp://localhost:{{port}}", "Copenhagen");
        assertEquals("Hello Claus", out);
    }

    @Test
    public void testNoResponse() throws Exception {
        try {
            template.requestBody("netty:tcp://localhost:{{port}}", "London");
            fail("Should throw an exception");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause().getMessage().startsWith("No response"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty:tcp://localhost:{{port}}")
                    .choice()
                        .when(body().isEqualTo("Copenhagen")).transform(constant("Hello Claus"))
                        .otherwise().transform(constant(null));
            }
        };
    }
}
