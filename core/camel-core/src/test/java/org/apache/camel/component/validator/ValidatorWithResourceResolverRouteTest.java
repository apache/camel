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

import java.net.URL;

import org.w3c.dom.ls.LSResourceResolver;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.validation.CatalogLSResourceResolver;
import org.apache.camel.support.ResourceHelper;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.tools.CatalogResolver;
import org.junit.Before;
import org.junit.Test;

public class ValidatorWithResourceResolverRouteTest extends ContextTestSupport {

    protected MockEndpoint validEndpoint;
    protected MockEndpoint finallyEndpoint;
    protected MockEndpoint invalidEndpoint;

    @Test
    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);
        invalidEndpoint.expectedMessageCount(0);
        template
            .sendBody("direct:start",
                      "<report xmlns='http://foo.com/report' xmlns:rb='http://foo.com/report-base'><author><rb:name>Knuth</rb:name></author><content><rb:chapter><rb:subject></rb:subject>"
                                      + "<rb:abstract></rb:abstract><rb:body></rb:body></rb:chapter></content></report>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Test
    public void testInvalidMessage() throws Exception {
        validEndpoint.expectedMessageCount(0);
        invalidEndpoint.expectedMessageCount(1);
        finallyEndpoint.expectedMessageCount(1);

        template.sendBody("direct:start", "<report xmlns='http://foo.com/report' xmlns:rb='http://foo.com/report-base'><author><rb:name>Knuth</rb:name></author></report>");

        MockEndpoint.assertIsSatisfied(validEndpoint, invalidEndpoint, finallyEndpoint);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        invalidEndpoint = resolveMandatoryEndpoint("mock:invalid", MockEndpoint.class);
        finallyEndpoint = resolveMandatoryEndpoint("mock:finally", MockEndpoint.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        // we have to do it here, because we need the context created first
        CatalogManager.getStaticManager().setIgnoreMissingProperties(true);
        CatalogResolver catalogResolver = new CatalogResolver(true);
        URL catalogUrl = ResourceHelper.resolveMandatoryResourceAsUrl(context.getClassResolver(), "org/apache/camel/component/validator/catalog.cat");
        catalogResolver.getCatalog().parseCatalog(catalogUrl);
        LSResourceResolver resourceResolver = new CatalogLSResourceResolver(catalogResolver);
        context.getRegistry().bind("resourceResolver", resourceResolver);

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").doTry().to("validator:org/apache/camel/component/validator/report.xsd?resourceResolver=#resourceResolver").to("mock:valid")
                    .doCatch(ValidationException.class).to("mock:invalid").doFinally().to("mock:finally").end();
            }
        };
    }
}
