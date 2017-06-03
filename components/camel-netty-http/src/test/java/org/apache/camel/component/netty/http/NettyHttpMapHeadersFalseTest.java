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
package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.Test;

public class NettyHttpMapHeadersFalseTest extends BaseNettyTest {

    @Test
    public void testHttpHeaderCase() throws Exception {
        HttpClient client = new HttpClient();
        HttpMethod method = new PostMethod("http://localhost:" + getPort() + "/myapp/mytest");

        method.setRequestHeader("clientHeader", "fooBAR");
        method.setRequestHeader("OTHER", "123");
        method.setRequestHeader("beer", "Carlsberg");

        client.executeMethod(method);

        assertEquals("Bye World", method.getResponseBodyAsString());
        assertEquals("aBc123", method.getResponseHeader("MyCaseHeader").getValue());
        assertEquals("456DEf", method.getResponseHeader("otherCaseHeader").getValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty-http:http://localhost:{{port}}/myapp/mytest?mapHeaders=false").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // these headers is not mapped
                        assertNull(exchange.getIn().getHeader("clientHeader"));
                        assertNull(exchange.getIn().getHeader("OTHER"));
                        assertNull(exchange.getIn().getHeader("beer"));

                        // but we can find them in the http request from netty
                        assertEquals("fooBAR", exchange.getIn(NettyHttpMessage.class).getHttpRequest().headers().get("clientHeader"));
                        assertEquals("123", exchange.getIn(NettyHttpMessage.class).getHttpRequest().headers().get("OTHER"));
                        assertEquals("Carlsberg", exchange.getIn(NettyHttpMessage.class).getHttpRequest().headers().get("beer"));

                        exchange.getOut().setBody("Bye World");
                        exchange.getOut().setHeader("MyCaseHeader", "aBc123");
                        exchange.getOut().setHeader("otherCaseHeader", "456DEf");
                    }
                });
            }
        };
    }

}
