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
package org.apache.camel.component.netty.http;

import java.io.InputStream;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class NettyHttpConvertPayloadToInputStreamTest extends BaseNettyTest {

    protected String expectedBody = "<hello>world!</hello>";

    @Test
    public void testConvertPayloadToInputStream() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedMessageCount(1);

        template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/test", expectedBody, "Content-Type", "application/xml");

        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull("exchange", exchange);

        Message in = exchange.getIn();
        assertNotNull("in", in);

        Object actual = in.getBody();
        InputStream value = assertIsInstanceOf(InputStream.class, actual);
        assertNotNull("InputStream", value);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("netty-http:http://localhost:{{port}}/test").
                        convertBodyTo(InputStream.class).
                        to("mock:result");
            }
        };
    }
}
