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
package org.apache.camel.component.jhc;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * @version $Revision: 520220 $
 */
public class HttpTest extends ContextTestSupport {

    protected String expectedText = "<response/>";

    protected void setUp() throws Exception {
        super.setUp();
        context.getTypeConverter().convertTo(Integer.class, "3");
    }

    public void testHttpGet() throws Exception {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", null);

        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull("exchange", exchange);

        Message in = exchange.getIn();
        assertNotNull("in", in);

        Map<String, Object> headers = in.getHeaders();

        log.debug("Headers: " + headers);
        assertTrue("Should be more than one header but was: " + headers, headers.size() > 0);

        String body = in.getBody(String.class);

        log.debug("Body: " + body);
        assertNotNull("Should have a body!", body);
        assertTrue("body should contain: " + expectedText, body.contains(expectedText));
    }

    public void testUsingURL() throws Exception {
        for (int i = 0; i < 10; i++) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8192/").openConnection();
            //HttpURLConnection con = (HttpURLConnection) new URL("http://www.google.com/").openConnection();
            con.setDoInput(true);
            con.setDoOutput(false);
            con.connect();
            System.err.println("Message: " + con.getResponseMessage());
            InputStream is = con.getInputStream();
            byte[] buf = new byte[8192];
            int len;
            for (;;) {
                len = is.read(buf);
                if (len < 0) {
                    is.close();
                    break;
                }
                baos.write(buf, 0, len);
            }
            String str = baos.toString();
            System.err.println("Response: " + str);
            assertEquals("<response/>", str);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("jhc:http://localhost:8192/").setOutBody(constant("<response/>"));
                from("direct:start").to("jhc:http://localhost:8192/pom.xml").to("mock:results");
            }
        };
    }
}