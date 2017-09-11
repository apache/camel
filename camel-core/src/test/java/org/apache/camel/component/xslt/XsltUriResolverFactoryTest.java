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
package org.apache.camel.component.xslt;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.xml.XsltUriResolver;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.Assert;

import static org.awaitility.Awaitility.await;

/**
 *
 */
public class XsltUriResolverFactoryTest extends ContextTestSupport {

    private JndiRegistry registry;

    public void testConfigurationOnEndpoint() throws Exception {
        String endpointUri = "xslt:xslt/staff/staff.xsl?uriResolverFactory=#uriResolverFactory";
        String directStart = "direct:start";

        // ensure that the URI resolver factory is not set on the component by
        // the method "testConfigurationOnComponent"
        registry.getContext().unbind("xslt");
        execute(endpointUri, directStart);
    }

    public void testConfigurationOnComponent() throws Exception {

        XsltComponent xsltComponent = new XsltComponent();
        xsltComponent.setUriResolverFactory(new CustomXsltUriResolverFactory());
        registry.bind("xslt", xsltComponent);

        String endpointUri = "xslt:xslt/staff/staff.xsl";
        String directStart = "direct:startComponent";

        execute(endpointUri, directStart);
    }

    void execute(String endpointUri, String directStart) throws InterruptedException {
        InputStream payloud = XsltUriResolverFactoryTest.class.getClassLoader().getResourceAsStream("xslt/staff/staff.xml");
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        sendBody(directStart, payloud);

        // wait until endpoint is resolved
        await().atMost(1, TimeUnit.SECONDS).until(() -> resolveMandatoryEndpoint(endpointUri, XsltEndpoint.class) != null);

        assertMockEndpointsSatisfied();

        XsltEndpoint xsltEndpoint = resolveMandatoryEndpoint(endpointUri, XsltEndpoint.class);
        assertNotNull(xsltEndpoint);

        CustomXsltUriResolver resolver = (CustomXsltUriResolver)xsltEndpoint.getUriResolver();
        checkResourceUri(resolver.resolvedResourceUris, "xslt/staff/staff.xsl");
        checkResourceUri(resolver.resolvedResourceUris, "../common/staff_template.xsl");
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start") //
                    .setHeader("xslt_file", new ConstantExpression("xslt/staff/staff.xsl")) //
                    .recipientList(new SimpleExpression("xslt:${header.xslt_file}?uriResolverFactory=#uriResolverFactory")) //
                    .to("mock:result");
            }
        }, new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:startComponent") //
                    .setHeader("xslt_file", new ConstantExpression("xslt/staff/staff.xsl")) //
                    .recipientList(new SimpleExpression("xslt:${header.xslt_file}")) //
                    .to("mock:result");
            }
        }};
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        registry = super.createRegistry();
        registry.bind("uriResolverFactory", new CustomXsltUriResolverFactory());
        return registry;

    }

    void checkResourceUri(Set<String> uris, String resourceUri) {
        Assert.assertTrue("Missing resource uri " + resourceUri + " in resolved resource URI set", uris.contains(resourceUri));
    }

    static class CustomXsltUriResolverFactory implements XsltUriResolverFactory {
        @Override
        public URIResolver createUriResolver(CamelContext camelContext, String resourceUri) {
            return new CustomXsltUriResolver(camelContext, resourceUri);
        }
    }

    static class CustomXsltUriResolver extends XsltUriResolver {
        private final Set<String> resolvedResourceUris = new HashSet<>();

        CustomXsltUriResolver(CamelContext context, String location) {
            super(context, location);
        }

        public Source resolve(String href, String base) throws TransformerException {
            Source result = super.resolve(href, base);
            resolvedResourceUris.add(href);
            return result;
        }
    }
}
