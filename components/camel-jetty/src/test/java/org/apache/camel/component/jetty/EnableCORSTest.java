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
import org.apache.camel.test.AvailablePortFinder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnableCORSTest extends BaseJettyTest {

    @RegisterExtension
    static AvailablePortFinder.Port port3 = AvailablePortFinder.find();

    private static int getPort3() {
        return port3.getPort();
    }

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

    /**
     * enableCORS on its own reflects the request origin, which is what makes CORS work at all, but must not also grant
     * credentials: reflecting the origin is the usual way around the fetch specification's refusal to pair "*" with
     * credentials, so the two together are the credentialed any-origin configuration.
     */
    @Test
    public void testCORSenabledDoesNotGrantCredentials() throws Exception {
        HttpGet httpMethod = new HttpGet("http://localhost:" + getPort2() + "/test2");
        httpMethod.addHeader("Origin", "http://localhost:9000");
        httpMethod.addHeader("Referer", "http://localhost:9000");

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(httpMethod)) {

            assertEquals(200, response.getCode(), "Get a wrong response status");

            // the origin is still reflected, so CORS itself keeps working
            assertEquals("http://localhost:9000", response.getFirstHeader("Access-Control-Allow-Origin").getValue());

            Object credentials = response.getFirstHeader("Access-Control-Allow-Credentials");
            assertTrue(credentials == null
                    || !Boolean.parseBoolean(response.getFirstHeader("Access-Control-Allow-Credentials").getValue()),
                    "credentials must not be granted to an origin the operator did not name");
        }
    }

    @Test
    public void testCORSCredentialsCanBeAskedFor() throws Exception {
        HttpGet httpMethod = new HttpGet("http://localhost:" + getPort3() + "/test3");
        httpMethod.addHeader("Origin", "http://localhost:9000");
        httpMethod.addHeader("Referer", "http://localhost:9000");

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(httpMethod)) {

            assertEquals(200, response.getCode(), "Get a wrong response status");

            String responseHeader = response.getFirstHeader("Access-Control-Allow-Credentials").getValue();
            assertTrue(Boolean.parseBoolean(responseHeader), "credentials should be granted when configured");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty://http://localhost:{{port}}/test1?enableCORS=false").transform(simple("OK"));
                from("jetty://http://localhost:{{port2}}/test2?enableCORS=true").transform(simple("OK"));
                from("jetty://http://localhost:" + getPort3() + "/test3?enableCORS=true"
                     + "&filterInit.allowedOrigins=http://localhost:9000"
                     + "&filterInit.allowCredentials=true").transform(simple("OK"));
            }
        };
    }
}
