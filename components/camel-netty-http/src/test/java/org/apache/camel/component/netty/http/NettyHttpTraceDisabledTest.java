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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;

public class NettyHttpTraceDisabledTest extends BaseNettyTest {

    private int portTraceOn = getNextPort();
    private int portTraceOff = getNextPort();

    @Test
    public void testTraceDisabled() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpTrace trace = new HttpTrace("http://localhost:" + portTraceOff + "/myservice");

            try (CloseableHttpResponse response = client.execute(trace)) {
                // TRACE shouldn't be allowed by default
                assertEquals(405, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Test
    public void testTraceEnabled() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpTrace trace = new HttpTrace("http://localhost:" + portTraceOn + "/myservice");

            try (CloseableHttpResponse response = client.execute(trace)) {
                // TRACE is allowed
                assertEquals(200, response.getStatusLine().getStatusCode());
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty-http:http://localhost:" + portTraceOff + "/myservice").to("log:foo");
                from("netty-http:http://localhost:" + portTraceOn + "/myservice?traceEnabled=true").to("log:bar");
            }
        };
    }

}
