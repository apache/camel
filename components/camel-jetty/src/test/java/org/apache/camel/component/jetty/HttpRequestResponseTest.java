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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpMessage;
import org.junit.Test;

/**
 * Unit test for request response in message
 */
public class HttpRequestResponseTest extends BaseJettyTest {

    @Test
    public void testHttpServletRequestResponse() throws Exception {
        Object response = template.requestBody("http://localhost:{{port}}/myapp/myservice", "bookid=123");
        // convert the response to a String
        String body = context.getTypeConverter().convertTo(String.class, response);
        assertEquals("Written by servlet response<html><body>Book 123 is Camel in Action</body></html>", body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("jetty:http://localhost:{{port}}/myapp/myservice").process(new MyBookService());
                // END SNIPPET: e1
            }
        };
    }

    public class MyBookService implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            // just get the body as a string
            String body = exchange.getIn().getBody(String.class);

            // we have access to the HttpServletRequest here and we can grab it
            // if we need it
            HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
            assertNotNull(req);

            // we have access to the HttpServletResponse here and we can grab it
            // if we need it
            HttpServletResponse res = exchange.getIn().getBody(HttpServletResponse.class);
            assertNotNull(res);

            // and they should also be on HttpMessage
            HttpMessage msg = (HttpMessage)exchange.getIn();
            assertNotNull(msg.getRequest());
            assertNotNull(msg.getResponse());

            // and we can use servlet response to write to output stream also
            res.getOutputStream().print("Written by servlet response");

            // for unit testing
            assertEquals("bookid=123", body);

            // send a html response
            exchange.getOut().setBody("<html><body>Book 123 is Camel in Action</body></html>");
        }
    }

}
