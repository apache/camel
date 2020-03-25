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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class NettyHttpHeaderCaseTest extends BaseNettyTest {

    @Test
    public void testHttpHeaderCase() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost method = new HttpPost("http://localhost:" + getPort() + "/myapp/mytest");

            method.addHeader("clientHeader", "fooBAR");
            method.addHeader("OTHER", "123");
            method.addHeader("beer", "Carlsberg");

            try (CloseableHttpResponse response = client.execute(method)) {
                String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");

                assertEquals("Bye World", responseString);
                assertEquals("aBc123", response.getFirstHeader("MyCaseHeader").getValue());
                assertEquals("456DEf", response.getFirstHeader("otherCaseHeader").getValue());
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty-http:http://localhost:{{port}}/myapp/mytest").process(exchange -> {

                    // headers received should be in case as well
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.putAll(exchange.getIn().getHeaders());

                    assertEquals("123", map.get("OTHER"));
                    assertEquals(null, map.get("other"));
                    assertEquals("Carlsberg", map.get("beer"));
                    assertEquals(null, map.get("Beer"));

                    exchange.getMessage().setBody("Bye World");
                    exchange.getMessage().setHeader("MyCaseHeader", "aBc123");
                    exchange.getMessage().setHeader("otherCaseHeader", "456DEf");
                });
            }
        };
    }

}
