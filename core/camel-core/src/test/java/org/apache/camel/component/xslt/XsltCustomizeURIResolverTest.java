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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.ResourceHelper;
import org.junit.Test;

/**
 *
 */
public class XsltCustomizeURIResolverTest extends ContextTestSupport {

    private static final String EXPECTED_XML_CONSTANT = "<data>FOO DATA</data>";

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
                from("file:src/test/data/?fileName=staff.xml&noop=true&initialDelay=0&delay=10")
                    .to("xslt:org/apache/camel/component/xslt/include_not_existing_resource.xsl?uriResolver=#customURIResolver").to("mock:resultURIResolverDirect");
            }
        };
    }

    private URIResolver getCustomURIResolver() {
        return new URIResolver() {

            @Override
            public Source resolve(String href, String base) throws TransformerException {
                if (href.equals("org/apache/camel/component/xslt/include_not_existing_resource.xsl")) {
                    try {
                        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, href);
                        return new StreamSource(is);
                    } catch (Exception e) {
                        throw new TransformerException(e);
                    }
                }

                Source constantResult = new StreamSource(new ByteArrayInputStream(EXPECTED_XML_CONSTANT.getBytes()));
                return constantResult;
            }
        };
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        URIResolver customURIResolver = getCustomURIResolver();
        registry.bind("customURIResolver", customURIResolver);
        return registry;
    }
}
