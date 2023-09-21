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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltFromHeaderOverrideEndpointUriTest extends ContextTestSupport {

    @Test
    public void testSendMessageAndHaveItTransformed() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start",
                "<mail><subject>Hey</subject><body>Hello world!</body></mail>",
                XsltConstants.XSLT_RESOURCE_URI, "org/apache/camel/component/xslt/transform_to_foo.xsl");

        assertMockEndpointsSatisfied();

        List<Exchange> list = endpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        String xml = exchange.getIn().getBody(String.class);

        assertNotNull("The transformed XML should not be null", xml);
        assertTrue(xml.contains("transformed"));
        // the foo tag is in the transform_to_foo.xsl which is what we want. If this didn't
        // work then transform.xsl would be used and we'd have a cheese element
        assertTrue(xml.contains("foo"));
        assertTrue(xml.contains("<subject>Hey</subject>"));
        assertTrue(xml.contains("<body>Hello world!</body>"));

        TestBean bean = context.getRegistry().lookupByNameAndType("testBean", TestBean.class);
        assertNotNull(bean);
        assertEquals("Hey", bean.getSubject());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("xslt:org/apache/camel/component/xslt/transform.xsl?allowTemplateFromHeader=true")
                        .multicast()
                        .bean("testBean")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("testBean", new TestBean());
        return context;
    }

}
