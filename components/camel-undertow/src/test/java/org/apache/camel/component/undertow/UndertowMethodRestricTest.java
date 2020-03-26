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
package org.apache.camel.component.undertow;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class UndertowMethodRestricTest extends BaseUndertowTest {

    private static String url;

    @BeforeClass
    public static void init() {
        url = "http://localhost:" + getPort() + "/methodRestrict";
    }

    @Test
    public void testProperHttpMethod() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new StringEntity("This is a test"));

        HttpResponse response = client.execute(httpPost);

        assertEquals(200, response.getStatusLine().getStatusCode());
        String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
        assertEquals("This is a test response", responseString);

        client.close();
    }

    @Test
    public void testImproperHttpMethod() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpGet httpGet = new HttpGet(url);
        HttpResponse response = client.execute(httpGet);
        int status = response.getStatusLine().getStatusCode();

        assertEquals("Get a wrong response status", 405, status);

        client.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("undertow://http://localhost:{{port}}/methodRestrict?httpMethodRestrict=POST").process(exchange -> {
                    Message in = exchange.getIn();
                    String request = in.getBody(String.class);
                    exchange.getMessage().setBody(request + " response");
                });
            }
        };
    }
}
