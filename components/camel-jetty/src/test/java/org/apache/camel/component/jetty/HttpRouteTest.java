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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class HttpRouteTest extends ContextTestSupport {
    protected String expectedBody = "<hello>world!</hello>";

    public void testEndpoint() throws Exception {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived(expectedBody);

        invokeHttpEndpoint();

        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull("exchange", exchange);

        Message in = exchange.getIn();
        assertNotNull("in", in);

        Map<String, Object> headers = in.getHeaders();

        log.info("Headers: " + headers);

        assertTrue("Should be more than one header but was: " + headers, headers.size() > 0);
    }

    public void testHelloEndpoint() throws Exception {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InputStream is = new URL("http://localhost:8080/hello").openStream();
        int c;
        while ((c = is.read()) >= 0) {
            os.write(c);
        }

        String data = new String(os.toByteArray());
        assertEquals("<b>Hello World</b>", data);
        
    }
    
    protected void invokeHttpEndpoint() throws IOException {
        template.sendBodyAndHeader("http://localhost:8080/test", expectedBody, "Content-Type", "application/xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:http://localhost:8080/test").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut(true).setBody("<b>Hello World</b>");
                    }
                };
                from("jetty:http://localhost:8080/hello").process(proc);
            }
        };
    }
}
