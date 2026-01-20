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

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class NettyHttpTwoRoutesValidateBootstrapConfigurationTest extends BaseNettyTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testTwoRoutes() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo?reconnect=false")
                        .to("mock:foo")
                        .transform().constant("Bye World");

                // we cannot have a 2nd route on same port with different option that the 1st route
                from("netty-http:http://0.0.0.0:{{port}}/bar?reconnect=true")
                        .to("mock:bar")
                        .transform().constant("Bye Camel");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Bootstrap configuration must be identical when adding additional consumer"));
        }
    }

}
