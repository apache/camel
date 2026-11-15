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

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerFactory;

import net.sf.saxon.TransformerFactoryImpl;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.xslt.TransformerFactoryConfigurationStrategy;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The transformerFactoryConfigurationStrategy and transformerFactoryClass options of the component apply to the
 * endpoints that do not set their own.
 */
public class XsltSaxonComponentFactoryOptionsTest extends CamelTestSupport {

    private static final String XSL = "xslt-saxon:xslt/transform.xsl";

    private final List<TransformerFactory> configuredFactories = new ArrayList<>();

    @BindToRegistry("recordFactory")
    private final TransformerFactoryConfigurationStrategy recordFactory
            = (factory, endpoint) -> configuredFactories.add(factory);

    @Test
    public void componentStrategyIsAppliedToTheFactory() throws Exception {
        context.getComponent("xslt-saxon", XsltSaxonComponent.class)
                .setTransformerFactoryConfigurationStrategy(recordFactory);
        addRouteAndSend(XSL);

        assertEquals(1, configuredFactories.size());
        assertInstanceOf(TransformerFactoryImpl.class, configuredFactories.get(0));
    }

    @Test
    public void componentTransformerFactoryClassIsUsed() throws Exception {
        context.getComponent("xslt-saxon", XsltSaxonComponent.class)
                .setTransformerFactoryClass(CustomTransformerFactory.class.getName());
        addRouteAndSend(XSL + "?transformerFactoryConfigurationStrategy=#recordFactory");

        assertEquals(1, configuredFactories.size());
        assertInstanceOf(CustomTransformerFactory.class, configuredFactories.get(0));
    }

    private void addRouteAndSend(String uri) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(uri).to("mock:result");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", "<mail><subject>Hey</subject><body>Hello world!</body></mail>");
        MockEndpoint.assertIsSatisfied(context);
    }

    public static class CustomTransformerFactory extends TransformerFactoryImpl {
    }
}
