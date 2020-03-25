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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class NettyHttpMethodRestrictTest extends BaseNettyTest {

    private String getUrl() {
        return "http://localhost:" + getPort() + "/methodRestrict";
    }

    @Test
    public void testProperHttpMethod() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(getUrl());
            httpPost.setEntity(new StringEntity("This is a test"));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                assertEquals("Get a wrong response status", 200, response.getStatusLine().getStatusCode());

                String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
                assertEquals("Get a wrong result", "This is a test response", responseString);
            }
        }
    }

    @Test
    public void testImproperHttpMethod() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(getUrl());
            try (CloseableHttpResponse response = client.execute(httpGet)) {
                assertEquals("Get a wrong response status", 405, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty-http:http://localhost:{{port}}/methodRestrict?httpMethodRestrict=POST").process(exchange -> {
                    Message in = exchange.getIn();
                    String request = in.getBody(String.class);
                    exchange.getMessage().setBody(request + " response");
                });
            }
        };
    }

}
