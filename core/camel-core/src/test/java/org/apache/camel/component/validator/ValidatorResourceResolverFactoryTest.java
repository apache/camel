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
package org.apache.camel.component.validator;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultRegistry;
import org.apache.camel.support.jndi.JndiBeanRepository;
import org.junit.Assert;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

public class ValidatorResourceResolverFactoryTest extends ContextTestSupport {

    private Context jndiContext;

    @Test
    public void testConfigurationOnEndpoint() throws Exception {
        // ensure that validator from test method "testConfigurationOnComponent"
        // is unbind
        jndiContext.unbind("validator");

        String directStart = "direct:start";
        String endpointUri = "validator:org/apache/camel/component/validator/xsds/person.xsd?resourceResolverFactory=#resourceResolverFactory";

        execute(directStart, endpointUri);
    }

    @Test
    public void testConfigurationOnComponent() throws Exception {
        // set resource resolver factory on component
        ValidatorComponent validatorComponent = new ValidatorComponent();
        validatorComponent.setResourceResolverFactory(new ResourceResolverFactoryImpl());
        jndiContext.bind("validator", validatorComponent);

        String directStart = "direct:startComponent";
        String endpointUri = "validator:org/apache/camel/component/validator/xsds/person.xsd";
        execute(directStart, endpointUri);

    }

    void execute(String directStart, String endpointUri) throws InterruptedException {
        MockEndpoint endEndpoint = resolveMandatoryEndpoint("mock:end", MockEndpoint.class);
        endEndpoint.expectedMessageCount(1);

        final String body = "<p:person user=\"james\" xmlns:p=\"org.person\" xmlns:h=\"org.health.check.person\" xmlns:c=\"org.health.check.common\">\n" //
                            + "  <p:firstName>James</p:firstName>\n" //
                            + "  <p:lastName>Strachan</p:lastName>\n" //
                            + "  <p:city>London</p:city>\n" //
                            + "  <h:health>\n"//
                            + "      <h:lastCheck>2011-12-23</h:lastCheck>\n" //
                            + "      <h:status>OK</h:status>\n" //
                            + "      <c:commonElement>" //
                            + "          <c:element1/>" //
                            + "          <c:element2/>" //
                            + "      </c:commonElement>" //
                            + "  </h:health>\n" //
                            + "</p:person>";

        template.sendBody(directStart, body);

        // wait until endpoint is resolved
        await().atMost(1, TimeUnit.SECONDS).until(() -> resolveMandatoryEndpoint(endpointUri, ValidatorEndpoint.class) != null);

        MockEndpoint.assertIsSatisfied(endEndpoint);

        ValidatorEndpoint validatorEndpoint = resolveMandatoryEndpoint(endpointUri, ValidatorEndpoint.class);
        Assert.assertNotNull(validatorEndpoint);
        CustomResourceResolver resolver = (CustomResourceResolver)validatorEndpoint.getResourceResolver();

        Set<String> uris = resolver.getResolvedResourceUris();
        checkResourceUri(uris, "../type2.xsd");
        checkResourceUri(uris, "health/health.xsd");
        checkResourceUri(uris, "type1.xsd");
        checkResourceUri(uris, "common/common.xsd");
    }

    void checkResourceUri(Set<String> uris, String resourceUri) {
        Assert.assertTrue("Missing resource uri " + resourceUri + " in resolved resource URI set", uris.contains(resourceUri));
    }

    @Override
    protected Registry createRegistry() throws Exception {
        jndiContext = createJndiContext();
        jndiContext.bind("resourceResolverFactory", new ResourceResolverFactoryImpl());
        return new DefaultRegistry(new JndiBeanRepository(jndiContext));

    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setHeader("xsd_file", new ConstantExpression("org/apache/camel/component/validator/xsds/person.xsd"))
                    .recipientList(new SimpleExpression("validator:${header.xsd_file}?resourceResolverFactory=#resourceResolverFactory")).to("mock:end");
            }

        }, new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:startComponent").setHeader("xsd_file", new ConstantExpression("org/apache/camel/component/validator/xsds/person.xsd"))
                    .recipientList(new SimpleExpression("validator:${header.xsd_file}")).to("mock:end");

            }
        }};
    }

    static class ResourceResolverFactoryImpl implements ValidatorResourceResolverFactory {

        @Override
        public LSResourceResolver createResourceResolver(CamelContext camelContext, String rootResourceUri) {
            return new CustomResourceResolver(camelContext, rootResourceUri);
        }

    }

    /** Custom resource resolver which collects all resolved resource URIs. */
    static class CustomResourceResolver extends DefaultLSResourceResolver {

        private final Set<String> resolvedRsourceUris = new HashSet<>();

        CustomResourceResolver(CamelContext camelContext, String resourceUri) {
            super(camelContext, resourceUri);
        }

        public Set<String> getResolvedResourceUris() {
            return resolvedRsourceUris;
        }

        @Override
        public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
            LSInput result = super.resolveResource(type, namespaceURI, publicId, systemId, baseURI);
            resolvedRsourceUris.add(systemId);
            return result;
        }

    }

}
