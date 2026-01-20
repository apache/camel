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

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NettyHttpMethodRestrictTest extends BaseNettyTestSupport {

    private String getUrl() {
        return "http://localhost:" + getPort() + "/methodRestrict";
    }

    @Test
    public void testProperHttpMethod() throws Exception {
        HttpPost httpPost = new HttpPost(getUrl());
        httpPost.setEntity(new StringEntity("This is a test"));
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(httpPost)) {
            assertEquals(200, response.getCode(), "Get a wrong response status");

            String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
            assertEquals("This is a test response", responseString, "Get a wrong result");
        }
    }

    @Test
    public void testImproperHttpMethod() throws Exception {
        HttpGet httpGet = new HttpGet(getUrl());
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(httpGet)) {
            assertEquals(405, response.getCode(), "Get a wrong response status");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("netty-http:http://localhost:{{port}}/methodRestrict?httpMethodRestrict=POST").process(exchange -> {
                    Message in = exchange.getIn();
                    String request = in.getBody(String.class);
                    exchange.getMessage().setBody(request + " response");
                });
            }
        };
    }

}
