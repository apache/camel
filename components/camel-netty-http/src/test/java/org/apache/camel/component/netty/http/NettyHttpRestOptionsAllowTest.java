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
import org.apache.camel.http.common.HttpMethods;
import org.junit.Test;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;

public class NettyHttpRestOptionsAllowTest extends BaseNettyTest {

    static final String ALLOW_METHODS = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";

    @Test
    public void shouldGetAllowMethods() throws Exception {
        Exchange response = template.request("netty-http:http://localhost:{{port}}/myapp", exchange -> {
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.OPTIONS);
            exchange.getIn().setBody("");
        });
        String body = response.getMessage().getBody(String.class);
        String allowHeader = (String) response.getMessage().getHeader("Allow");
        int code = (int) response.getMessage().getHeader(HTTP_RESPONSE_CODE);
        assertEquals(ALLOW_METHODS, allowHeader);
        assertEquals(200, code);
        assertEquals("", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://0.0.0.0:{{port}}/myapp").setBody().constant("options");
            }
        };
    }

}
