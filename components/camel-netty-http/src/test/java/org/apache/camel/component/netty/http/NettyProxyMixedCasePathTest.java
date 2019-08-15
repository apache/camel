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
import org.junit.Test;

public class NettyProxyMixedCasePathTest extends BaseNettyTest {

    @Test
    public void testMixedCase() throws Exception {
        String out = template.requestBody("netty-http:http://localhost:{{port}}/Shopping", "Camel", String.class);
        assertEquals("Bye Camel", out);

        out = template.requestBody("netty-http:http://localhost:{{port}}/shopping", "World", String.class);
        assertEquals("Bye World", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/Shopping").to("netty-http:http://localhost:{{port}}/ws/svc/Shopping");

                from("netty-http:http://0.0.0.0:{{port}}/ws/svc/Shopping").transform(body().prepend("Bye "));
            }
        };
    }
}
