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

import javax.xml.XMLConstants;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltSaxonSecureProcessingTest extends CamelTestSupport {

    @Test
    public void testSecureProcessingEnabledByDefault() throws Exception {
        XsltSaxonEndpoint endpoint
                = context.getEndpoint("xslt-saxon:xslt/transform.xsl", XsltSaxonEndpoint.class);
        assertTrue(endpoint.isSecureProcessing());

        TransformerFactoryImpl factory = new TransformerFactoryImpl();
        XsltSaxonHelper.configureSecureProcessing(factory, true);
        assertTrue(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    public void testSecureProcessingCanBeDisabled() throws Exception {
        XsltSaxonEndpoint endpoint
                = context.getEndpoint("xslt-saxon:xslt/transform.xsl?secureProcessing=false", XsltSaxonEndpoint.class);
        assertFalse(endpoint.isSecureProcessing());

        TransformerFactoryImpl factory = new TransformerFactoryImpl();
        XsltSaxonHelper.configureSecureProcessing(factory, false);
        assertFalse(factory.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING));
    }

    @Test
    public void testTransformWithSecureProcessing() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:default", "<mail><subject>Hey</subject><body>Hello world!</body></mail>");
        getMockEndpoint("mock:result").assertIsSatisfied();

        String xml = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(xml.contains("transformed"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default")
                        .to("xslt-saxon:xslt/transform.xsl")
                        .to("mock:result");
            }
        };
    }
}
