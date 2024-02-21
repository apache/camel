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
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NettyHttpMapHeadersFalseTest extends BaseNettyTest {

    @Test
    public void testHttpHeaderCase() throws Exception {
        HttpPost method = new HttpPost("http://localhost:" + getPort() + "/myapp/mytest");

        method.addHeader("clientHeader", "fooBAR");
        method.addHeader("OTHER", "123");
        method.addHeader("beer", "Carlsberg");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(method)) {
            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            assertEquals("Bye World", responseString);
            assertEquals("aBc123", response.getFirstHeader("MyCaseHeader").getValue());
            assertEquals("456DEf", response.getFirstHeader("otherCaseHeader").getValue());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty-http:http://localhost:{{port}}/myapp/mytest?mapHeaders=false").process(exchange -> {
                    // these headers is not mapped
                    assertNull(exchange.getIn().getHeader("clientHeader"));
                    assertNull(exchange.getIn().getHeader("OTHER"));
                    assertNull(exchange.getIn().getHeader("beer"));

                    // but we can find them in the http request from netty
                    assertEquals("fooBAR",
                            exchange.getIn(NettyHttpMessage.class).getHttpRequest().headers().get("clientHeader"));
                    assertEquals("123", exchange.getIn(NettyHttpMessage.class).getHttpRequest().headers().get("OTHER"));
                    assertEquals("Carlsberg", exchange.getIn(NettyHttpMessage.class).getHttpRequest().headers().get("beer"));

                    exchange.getMessage().setBody("Bye World");
                    exchange.getMessage().setHeader("MyCaseHeader", "aBc123");
                    exchange.getMessage().setHeader("otherCaseHeader", "456DEf");
                });
            }
        };
    }

}
