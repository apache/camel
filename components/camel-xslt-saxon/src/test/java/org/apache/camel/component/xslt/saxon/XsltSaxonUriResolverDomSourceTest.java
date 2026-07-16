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

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import org.xml.sax.InputSource;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class XsltSaxonUriResolverDomSourceTest extends CamelTestSupport {

    private static final String XSL = "<?xml version='1.0'?>"
                                      + "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='2.0'>"
                                      + "  <xsl:output method='xml' indent='yes'/>"
                                      + "  <xsl:template match='/'>"
                                      + "    <transformed><xsl:copy-of select='*'/></transformed>"
                                      + "  </xsl:template>"
                                      + "</xsl:stylesheet>";

    @Test
    public void testUriResolverReturningDomSource() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "<hello>world</hello>");

        MockEndpoint.assertIsSatisfied(context);

        String xml = mock.getReceivedExchanges().get(0).getIn().getBody(String.class);
        assertTrue(xml.contains("<transformed"), "Should contain transformed element");
        assertTrue(xml.contains("<hello>world</hello>"), "Should contain original content");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("xslt-saxon:dummy.xsl?uriResolver=#domResolver")
                        .to("mock:result");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document xslDoc = db.parse(new InputSource(new StringReader(XSL)));

        URIResolver domResolver = new URIResolver() {
            @Override
            public Source resolve(String href, String base) throws TransformerException {
                return new DOMSource(xslDoc);
            }
        };

        registry.bind("domResolver", domResolver);
    }
}
