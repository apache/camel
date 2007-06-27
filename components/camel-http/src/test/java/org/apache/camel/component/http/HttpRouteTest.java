/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.http;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

/**
 * @version $Revision: 520220 $
 */
public class HttpRouteTest extends ContextTestSupport {
    protected String expectedBody = "<hello>world!</hello>";

    public void testPojoRoutes() throws Exception {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        invokeHttpEndpoint();

        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull("exchange", exchange);

        Message in = exchange.getIn();
        assertNotNull("in", in);

        Map<String,Object> headers = in.getHeaders();
        String actualBody = in.getBody(String.class);

        log.info("Headers: " + headers);
        log.info("Received body: " + actualBody);

        assertEquals("Body", expectedBody, actualBody);
        assertTrue("Should be more than one header but was: " + headers, headers.size() > 0);
    }

    protected void invokeHttpEndpoint() throws IOException {
        template.sendBody("http://localhost:8080/test", expectedBody, "Content-Type", "application/xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("http://localhost:8080/test").convertBodyTo(String.class).to("mock:a");
            }
        };
    }
}
