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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpMethods;
import org.junit.Test;

import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;

public class NettyHttpRestOptionsAllowTest extends BaseNettyTest {
    
    static final String ALLOW_METHODS = "GET,HEAD,POST,PUT,DELETE,TRACE,OPTIONS,CONNECT,PATCH";

    @Test
    public void shouldGetAllowMethods() throws Exception {
        Exchange response = template.request("netty4-http:http://localhost:{{port}}/myapp", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.OPTIONS);
                exchange.getIn().setBody("");
            }
        });
        String body = response.getOut().getBody(String.class);
        String allowHeader = (String) response.getOut().getHeader("Allow");
        int code = (int) response.getOut().getHeader(HTTP_RESPONSE_CODE);
        assertEquals(ALLOW_METHODS, allowHeader);
        assertEquals(200, code);
        assertEquals("", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://0.0.0.0:{{port}}/myapp").setBody().constant("options");
            }
        };
    }

}
