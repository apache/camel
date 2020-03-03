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

import java.io.IOException;
import java.io.StringReader;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class XsltCustomizeEntityResolverTest extends ContextTestSupport {

    private static final String EXPECTED_XML_CONSTANT = "<A>1</A>";

    @Test
    public void testXsltCustomURIResolverDirectInRouteUri() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultURIResolverDirect");
        mock.expectedMessageCount(1);

        mock.message(0).body().contains(EXPECTED_XML_CONSTANT);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/test/data/?fileName=xml_with_entity.xml&noop=true&initialDelay=0&delay=10")
                    .to("xslt:xslt/common/copy.xsl?output=string&entityResolver=#customEntityResolver").to("mock:resultURIResolverDirect");
            }
        };
    }

    private EntityResolver getCustomEntityResolver() {
        return new EntityResolver() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                return new InputSource(new StringReader("<!ELEMENT A (#PCDATA)>"));
            }
        };
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        EntityResolver customEntityResolver = getCustomEntityResolver();
        registry.bind("customEntityResolver", customEntityResolver);
        return registry;
    }
}
