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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the XSLT source option is respected when the message body is an InputStream or StreamCache, ensuring the
 * configured source expression is evaluated instead of silently transforming the body.
 */
public class XsltSaxonSourceInputStreamBodyTest extends CamelTestSupport {

    private static final String HEADER_XML = "<mail><subject>FromHeader</subject><body>header body</body></mail>";
    private static final String BODY_XML = "<mail><subject>FromBody</subject><body>message body</body></mail>";

    @Test
    public void testSourceHeaderWithInputStreamBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sourceHeader");
        mock.expectedMessageCount(1);

        template.send("direct:sourceHeader", exchange -> {
            exchange.getIn().setHeader("xmlSource", HEADER_XML);
            exchange.getIn().setBody(new ByteArrayInputStream(BODY_XML.getBytes(StandardCharsets.UTF_8)));
        });

        MockEndpoint.assertIsSatisfied(context);

        String xml = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(xml, "The transformed XML should not be null");
        assertTrue(xml.contains("<subject>FromHeader</subject>"), "Should transform header source, not body. Got: " + xml);
    }

    @Test
    public void testSourceVariableWithInputStreamBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sourceVariable");
        mock.expectedMessageCount(1);

        template.send("direct:sourceVariable", exchange -> {
            exchange.setVariable("xmlSource", HEADER_XML);
            exchange.getIn().setBody(new ByteArrayInputStream(BODY_XML.getBytes(StandardCharsets.UTF_8)));
        });

        MockEndpoint.assertIsSatisfied(context);

        String xml = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(xml, "The transformed XML should not be null");
        assertTrue(xml.contains("<subject>FromHeader</subject>"), "Should transform variable source, not body. Got: " + xml);
    }

    @Test
    public void testSourcePropertyWithInputStreamBody() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:sourceProperty");
        mock.expectedMessageCount(1);

        template.send("direct:sourceProperty", exchange -> {
            exchange.setProperty("xmlSource", HEADER_XML);
            exchange.getIn().setBody(new ByteArrayInputStream(BODY_XML.getBytes(StandardCharsets.UTF_8)));
        });

        MockEndpoint.assertIsSatisfied(context);

        String xml = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(xml, "The transformed XML should not be null");
        assertTrue(xml.contains("<subject>FromHeader</subject>"), "Should transform property source, not body. Got: " + xml);
    }

    @Test
    public void testSourceHeaderWithStreamCacheBody() throws Exception {
        context.setStreamCaching(true);

        MockEndpoint mock = getMockEndpoint("mock:sourceHeader");
        mock.expectedMessageCount(1);

        template.send("direct:sourceHeader", exchange -> {
            exchange.getIn().setHeader("xmlSource", HEADER_XML);
            exchange.getIn().setBody(new ByteArrayInputStream(BODY_XML.getBytes(StandardCharsets.UTF_8)));
        });

        MockEndpoint.assertIsSatisfied(context);

        String xml = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(xml, "The transformed XML should not be null");
        assertTrue(xml.contains("<subject>FromHeader</subject>"),
                "Should transform header source, not stream-cached body. Got: " + xml);
    }

    @Test
    public void testSourceVariableWithStreamCacheBody() throws Exception {
        context.setStreamCaching(true);

        MockEndpoint mock = getMockEndpoint("mock:sourceVariable");
        mock.expectedMessageCount(1);

        template.send("direct:sourceVariable", exchange -> {
            exchange.setVariable("xmlSource", HEADER_XML);
            exchange.getIn().setBody(new ByteArrayInputStream(BODY_XML.getBytes(StandardCharsets.UTF_8)));
        });

        MockEndpoint.assertIsSatisfied(context);

        String xml = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertNotNull(xml, "The transformed XML should not be null");
        assertTrue(xml.contains("<subject>FromHeader</subject>"),
                "Should transform variable source, not stream-cached body. Got: " + xml);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:sourceHeader")
                        .to("xslt-saxon:xslt/transform.xsl?source=header:xmlSource")
                        .to("mock:sourceHeader");

                from("direct:sourceVariable")
                        .to("xslt-saxon:classpath:xslt/transform.xsl?source=variable:xmlSource")
                        .to("mock:sourceVariable");

                from("direct:sourceProperty")
                        .to("xslt-saxon:classpath:xslt/transform.xsl?source=property:xmlSource")
                        .to("mock:sourceProperty");
            }
        };
    }
}
