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

        String actualBody = in.getBody(String.class);

        log.info("Headers: " + in.getHeaders());
        log.info("Received body: " + actualBody);

        assertEquals("Body", expectedBody, actualBody);
    }

    protected void invokeHttpEndpoint() throws IOException {
        URL url = new URL("http://localhost:8080/test");
        URLConnection urlConnection = url.openConnection();
        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(true);
        urlConnection.setUseCaches(false);
        urlConnection.setRequestProperty("Content-Type", "application/xml");

        // Send POST data
        OutputStream out = urlConnection.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
        writer.write(expectedBody);
        writer.close();

        // read the response data
        BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            log.info("Read: " + line);
        }
        reader.close();

//        InputStream is = url.openConnection().getInputStream();
//        System.out.println("Content: "+is);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("http://localhost:8080/test").to("mock:a");
            }
        };
    }
}
