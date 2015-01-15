/**
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
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.TraceMethod;
import org.junit.Test;

public class JettyEndpointSetHttpTraceTest extends BaseJettyTest {

    private int portTraceOn = getNextPort();
    private int portTraceOff = getNextPort();

    @Test
    public void testTraceDisabled() throws Exception {        
        HttpClient httpclient = new HttpClient();
        TraceMethod trace = new TraceMethod("http://localhost:" + portTraceOff + "/myservice");
        httpclient.executeMethod(trace);

        // TRACE shouldn't be allowed by default
        assertTrue(trace.getStatusCode() == 405);
        trace.releaseConnection();
    }
    
    @Test
    public void testTraceEnabled() throws Exception {        
        HttpClient httpclient = new HttpClient();
        TraceMethod trace = new TraceMethod("http://localhost:" + portTraceOn + "/myservice");
        httpclient.executeMethod(trace);

        // TRACE is now allowed
        assertTrue(trace.getStatusCode() == 200);
        trace.releaseConnection();
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
