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
package org.apache.camel.component.xslt;

import java.util.List;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltTemplateFromHeaderOverrideEndpointUriTest extends CamelTestSupport {

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
        String sheet = IOHelper.loadText(XsltTemplateFromHeaderOverrideEndpointUriTest.class
                .getResourceAsStream("/org/apache/camel/component/xslt/transform.xsl"));
        Assertions.assertNotNull(sheet);

        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", body, XsltConstants.XSLT_STYLESHEET, sheet);

        MockEndpoint.assertIsSatisfied(context);

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull(xml, "The transformed XML should not be null");
        assertTrue(xml.contains("transformed"));
        // the cheese tag is in the transform.xsl
        assertTrue(xml.contains("cheese"));
        assertTrue(xml.contains("<subject>Hey</subject>"));
        assertTrue(xml.contains("<body>Hello world!</body>"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("xslt-saxon:dummy.xsl?contentCache=false&allowTemplateFromHeader=true").multicast()
                        .to("mock:result");
            }
        };
    }

}
