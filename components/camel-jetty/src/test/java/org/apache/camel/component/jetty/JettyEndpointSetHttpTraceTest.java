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
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;

public class JettyEndpointSetHttpTraceTest extends BaseJettyTest {

    private int portTraceOn = getNextPort();
    private int portTraceOff = getNextPort();

    @Test
    public void testTraceDisabled() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpTrace trace = new HttpTrace("http://localhost:" + portTraceOff + "/myservice");
        HttpResponse response = client.execute(trace);

        // TRACE shouldn't be allowed by default
        assertEquals(405, response.getStatusLine().getStatusCode());
        trace.releaseConnection();

        client.close();
    }

    @Test
    public void testTraceEnabled() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();

        HttpTrace trace = new HttpTrace("http://localhost:" + portTraceOn + "/myservice");
        HttpResponse response = client.execute(trace);

        // TRACE is allowed
        assertEquals(200, response.getStatusLine().getStatusCode());
        trace.releaseConnection();

        client.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:" + portTraceOff + "/myservice").to("log:foo");
                from("jetty:http://localhost:" + portTraceOn + "/myservice?traceEnabled=true").to("log:bar");
            }
        };
    }
}
