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

import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnableCORSTest extends BaseJettyTest {

    @Test
    public void testCORSdisabled() throws Exception {
        HttpGet httpMethod = new HttpGet("http://localhost:" + getPort() + "/test1");
        httpMethod.addHeader("Origin", "http://localhost:9000");
        httpMethod.addHeader("Referer", "http://localhost:9000");
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(httpMethod)) {

            assertEquals(200, response.getCode(), "Get a wrong response status");

            Object responseHeader = response.getFirstHeader("Access-Control-Allow-Credentials");
            assertNull(responseHeader, "Access-Control-Allow-Credentials HEADER should not be set");
        }
    }

    @Test
    public void testCORSenabled() throws Exception {
        HttpGet httpMethod = new HttpGet("http://localhost:" + getPort2() + "/test2");
        httpMethod.addHeader("Origin", "http://localhost:9000");
        httpMethod.addHeader("Referer", "http://localhost:9000");

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(httpMethod)) {

            assertEquals(200, response.getCode(), "Get a wrong response status");

            String responseHeader = response.getFirstHeader("Access-Control-Allow-Credentials").getValue();
            assertTrue(Boolean.parseBoolean(responseHeader), "CORS not enabled");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty://http://localhost:{{port}}/test1?enableCORS=false").transform(simple("OK"));
                from("jetty://http://localhost:{{port2}}/test2?enableCORS=true").transform(simple("OK"));
            }
        };
    }
}
