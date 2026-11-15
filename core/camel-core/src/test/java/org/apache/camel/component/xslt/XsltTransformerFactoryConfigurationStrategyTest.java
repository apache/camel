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

import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.XMLConstants;
import javax.xml.transform.TransformerConfigurationException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XsltTransformerFactoryConfigurationStrategyTest extends ContextTestSupport {

    private static final String XSL = "xslt:org/apache/camel/component/xslt/transformCallEcho.xsl";

    private final AtomicInteger strategyCalls = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        registry.bind("allowExtensionFunctions", allowExtensionFunctions());
        return registry;
    }

    @Test
    void endpointStrategyIsAppliedToTheDefaultFactory() throws Exception {
        startRoute(XSL + "?transformerFactoryConfigurationStrategy=#allowExtensionFunctions");

        assertTransformedWithExtensionFunction();
    }

    @Test
    void componentStrategyIsAppliedToTheDefaultFactory() throws Exception {
        context.getComponent("xslt", XsltComponent.class)
                .setTransformerFactoryConfigurationStrategy(allowExtensionFunctions());
        startRoute(XSL);

        assertTransformedWithExtensionFunction();
    }

    /**
     * A strategy that turns secure processing off, so that the stylesheet may call its extension function.
     */
    private TransformerFactoryConfigurationStrategy allowExtensionFunctions() {
        return (factory, endpoint) -> {
            strategyCalls.incrementAndGet();
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
            } catch (TransformerConfigurationException e) {
                throw new IllegalStateException(e);
            }
        };
    }

    private void startRoute(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(uri).to("mock:result");
            }
        });
        context.start();
    }

    private void assertTransformedWithExtensionFunction() throws Exception {
        assertThat(strategyCalls).hasValue(1);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "<name>Camel</name>");
        MockEndpoint.assertIsSatisfied(context);

        assertThat(mock.getReceivedExchanges().get(0).getIn().getBody(String.class))
                .contains("<cheese>Camel</cheese>");
    }
}
