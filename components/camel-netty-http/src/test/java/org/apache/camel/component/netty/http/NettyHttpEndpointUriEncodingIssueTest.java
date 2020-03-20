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

public class NettyHttpEndpointUriEncodingIssueTest extends BaseNettyTest {

    @Test
    public void testEndpointUriEncodingIssue() throws Exception {
        String uri = "netty-http:http://localhost:{{port}}/myapp/mytest?columns=totalsens,upsens&username=apiuser";
        String out = template.requestBody(uri, null, String.class);

        assertEquals("We got totalsens,upsens columns", out);
    }

    @Test
    public void testEndpointUriWithDanishCharEncodingIssue() throws Exception {
        String uri = "netty-http:http://localhost:{{port}}/myapp/mytest?columns=claus,s\u00F8ren&username=apiuser";
        String out = template.requestBody(uri, null, String.class);

        assertEquals("We got claus,s\u00F8ren columns", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://localhost:{{port}}/myapp/mytest?urlDecodeHeaders=true").process(exchange -> {
                    String columns = exchange.getIn().getHeader("columns", String.class);
                    exchange.getMessage().setBody("We got " + columns + " columns");
                });
            }
        };
    }

}
