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
package org.apache.camel.component.xslt.saxon;

import java.util.List;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class XsltSaxonTest extends CamelTestSupport {

    @Test
    public void testSendStringMessage() throws Exception {
        sendMessageAndHaveItTransformed("<mail><subject>Hey</subject><body>Hello world!</body></mail>");
    }

    @Test
    public void testSendBytesMessage() throws Exception {
        sendMessageAndHaveItTransformed("<mail><subject>Hey</subject><body>Hello world!</body></mail>".getBytes());
    }

    @Test
    public void testSendDomMessage() throws Exception {
        XmlConverter converter = new XmlConverter();
        Document body = converter.toDOMDocument("<mail><subject>Hey</subject><body>Hello world!</body></mail>", null);
        sendMessageAndHaveItTransformed(body);
    }

    private void sendMessageAndHaveItTransformed(Object body) throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(1);

        template.sendBody("direct:start", body);

        assertMockEndpointsSatisfied();

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull("The transformed XML should not be null", xml);
        assertTrue(xml.indexOf("transformed") > -1);
        // the cheese tag is in the transform.xsl
        assertTrue(xml.indexOf("cheese") > -1);
        assertTrue(xml.indexOf("<subject>Hey</subject>") > -1);
        assertTrue(xml.indexOf("<body>Hello world!</body>") > -1);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .to("xslt-saxon:xslt/transform.xsl")
                    .to("mock:result");
            }
        };
    }

}
