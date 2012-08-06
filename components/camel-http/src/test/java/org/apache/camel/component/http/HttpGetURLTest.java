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
package org.apache.camel.component.http;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("This test needs the internet connection, we can run it manually")
public class HttpGetURLTest extends CamelTestSupport {
    protected String expectedText = "Date,Open,High,Low,Close,Volume,Adj Close";
    
    @Test
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
        
        checkHeaders(headers);       

        String body = in.getBody(String.class);

        assertNotNull("Should have a body!", body);
        assertTrue("body should contain: " + expectedText, body.contains(expectedText));
    }

    protected void checkHeaders(Map<String, Object> headers) {
        assertTrue("Should be more than one header but was: " + headers, headers.size() > 0);
        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .setHeader("downloadUrl", constant("http://ichart.finance.yahoo.com/table.csv?s=MSFT&a=00&b=1&c=2005&d=06&e=29&f=2012&g=d&ignore=.csv"))
                    .setHeader("CamelHttpUri", simple("${in.header.downloadUrl}"))
                    .setHeader("User-Agent", constant("Mozilla"))
                    .to("http://some-invalid-host").to("mock:results");
            }
        };
    }
}
