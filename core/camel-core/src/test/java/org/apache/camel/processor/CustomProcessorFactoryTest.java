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
package org.apache.camel.processor;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.spi.ProcessorFactory;
import org.junit.Test;

public class CustomProcessorFactoryTest extends ContextTestSupport {

    @Override
    // START SNIPPET: e1
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // register our custom factory
        context.adapt(ExtendedCamelContext.class).setProcessorFactory(new MyFactory());
        return context;
    }
    // END SNIPPET: e1

    // START SNIPPET: e2
    @Test
    public void testAlterDefinitionUsingProcessorFactory() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("body was altered");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAlterDefinitionUsingProcessorFactoryWithChild() throws Exception {
        getMockEndpoint("mock:split").expectedBodiesReceived("body was altered", "body was altered");
        getMockEndpoint("mock:extra").expectedBodiesReceived("body was altered", "body was altered");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello,World");

        template.sendBody("direct:foo", "Hello,World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").setBody().constant("body not altered").to("mock:foo");

                from("direct:foo").split(body()).setBody().constant("body not altered").to("mock:split").end().to("mock:result");
            }
        };
    }
    // END SNIPPET: e2

    // START SNIPPET: e3
    public static class MyFactory implements ProcessorFactory {

        @Override
        public Processor createChildProcessor(Route route, NamedNode definition, boolean mandatory) throws Exception {
            return null;
        }

        @Override
        public Processor createProcessor(Route route, NamedNode definition) throws Exception {
            if (definition instanceof SplitDefinition) {
                // add additional output to the splitter
                SplitDefinition split = (SplitDefinition)definition;
                split.addOutput(new ToDefinition("mock:extra"));
            }

            if (definition instanceof SetBodyDefinition) {
                SetBodyDefinition set = (SetBodyDefinition)definition;
                set.setExpression(new ConstantExpression("body was altered"));
            }

            // return null to let the default implementation create the
            // processor, we just wanted to alter the definition
            // before the processor was created
            return null;
        }

        @Override
        public Processor createProcessor(CamelContext camelContext, String definitionName, Map<String, Object> args) throws Exception {
            return null;
        }
    }
    // END SNIPPET: e3

}
