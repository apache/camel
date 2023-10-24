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
package org.apache.camel.component.jetty;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class HttpHeaderCaseTest extends BaseJettyTest {

    @Test
    public void testHttpHeaderCase() throws Exception {
        HttpPost method = new HttpPost("http://localhost:" + getPort() + "/myapp/mytest");

        method.addHeader("clientHeader", "fooBAR");
        method.addHeader("OTHER", "123");
        method.addHeader("beer", "Carlsberg");

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(method)) {
            String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            assertEquals("Bye World", responseString);
            assertEquals("aBc123", response.getFirstHeader("MyCaseHeader").getValue());
            assertEquals("456DEf", response.getFirstHeader("otherCaseHeader").getValue());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:{{port}}/myapp/mytest").process(new Processor() {
                    public void process(Exchange exchange) {

                        // headers received should be in case as well
                        Map<String, Object> map = new LinkedHashMap<>(exchange.getIn().getHeaders());

                        assertEquals("123", map.get("OTHER"));
                        assertNull(map.get("other"));
                        assertEquals("Carlsberg", map.get("beer"));
                        assertNull(map.get("Beer"));

                        exchange.getMessage().setBody("Bye World");
                        exchange.getMessage().setHeader("MyCaseHeader", "aBc123");
                        exchange.getMessage().setHeader("otherCaseHeader", "456DEf");
                    }
                });
            }
        };
    }

}
