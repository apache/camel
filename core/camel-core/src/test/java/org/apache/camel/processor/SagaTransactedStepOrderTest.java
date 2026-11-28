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

import java.io.StringReader;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.camel.spi.Policy;
import org.apache.camel.xml.in.ModelParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A saga or transacted wraps the entire route, and the steps of the route keep the order they are written in: the steps
 * before the saga or transacted, then its own nested steps, then the steps after it.
 */
public class SagaTransactedStepOrderTest extends ContextTestSupport {

    private final Policy policy = new Policy() {
        @Override
        public void beforeWrap(Route route, NamedNode definition) {
            // noop
        }

        @Override
        public Processor wrap(Route route, Processor processor) {
            return processor;
        }
    };

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testJavaTransacted() throws Exception {
        context.getRegistry().bind("myPolicy", policy);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(simple("${body}-a"))
                        .transacted("myPolicy")
                        .setBody(simple("${body}-b"))
                        .setBody(simple("${body}-c"))
                        .to("mock:result");
            }
        });

        assertOrder(TransactedDefinition.class, "x-a-b-c");
    }

    @Test
    public void testJavaSaga() throws Exception {
        context.addService(new InMemorySagaService());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(simple("${body}-a"))
                        .saga()
                        .setBody(simple("${body}-b"))
                        .setBody(simple("${body}-c"))
                        .to("mock:result");
            }
        });

        assertOrder(SagaDefinition.class, "x-a-b-c");
    }

    @Test
    public void testJavaTransactedEndThenSiblingSteps() throws Exception {
        context.getRegistry().bind("myPolicy", policy);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody(simple("${body}-a"))
                        .transacted("myPolicy")
                            .setBody(simple("${body}-b"))
                        .end()
                        .setBody(simple("${body}-c"))
                        .to("mock:result");
            }
        });

        assertOrder(TransactedDefinition.class, "x-a-b-c");
    }

    @Test
    public void testXmlTransactedNestedStepsThenSiblingSteps() throws Exception {
        context.getRegistry().bind("myPolicy", policy);
        addXmlRoutes("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route>
                    <from uri="direct:start"/>
                    <setBody><simple>${body}-a</simple></setBody>
                    <transacted ref="myPolicy">
                      <setBody><simple>${body}-b</simple></setBody>
                    </transacted>
                    <setBody><simple>${body}-c</simple></setBody>
                    <to uri="mock:result"/>
                  </route>
                </routes>
                """);

        assertOrder(TransactedDefinition.class, "x-a-b-c");
    }

    @Test
    public void testXmlTransactedWithoutNestedSteps() throws Exception {
        context.getRegistry().bind("myPolicy", policy);
        addXmlRoutes("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route>
                    <from uri="direct:start"/>
                    <setBody><simple>${body}-a</simple></setBody>
                    <transacted ref="myPolicy"/>
                    <setBody><simple>${body}-b</simple></setBody>
                    <setBody><simple>${body}-c</simple></setBody>
                    <to uri="mock:result"/>
                  </route>
                </routes>
                """);

        assertOrder(TransactedDefinition.class, "x-a-b-c");
    }

    @Test
    public void testXmlSagaNestedStepsThenSiblingSteps() throws Exception {
        context.addService(new InMemorySagaService());
        addXmlRoutes("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route>
                    <from uri="direct:start"/>
                    <setBody><simple>${body}-a</simple></setBody>
                    <saga>
                      <setBody><simple>${body}-b</simple></setBody>
                    </saga>
                    <setBody><simple>${body}-c</simple></setBody>
                    <to uri="mock:result"/>
                  </route>
                </routes>
                """);

        assertOrder(SagaDefinition.class, "x-a-b-c");
    }

    @Test
    public void testXmlTransactedAndSagaNestedSteps() throws Exception {
        context.getRegistry().bind("myPolicy", policy);
        context.addService(new InMemorySagaService());
        addXmlRoutes("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route>
                    <from uri="direct:start"/>
                    <setBody><simple>${body}-a</simple></setBody>
                    <transacted ref="myPolicy">
                      <setBody><simple>${body}-b</simple></setBody>
                    </transacted>
                    <setBody><simple>${body}-c</simple></setBody>
                    <saga>
                      <setBody><simple>${body}-d</simple></setBody>
                    </saga>
                    <setBody><simple>${body}-e</simple></setBody>
                    <to uri="mock:result"/>
                  </route>
                </routes>
                """);

        assertOrder(TransactedDefinition.class, "x-a-b-c-d-e");
    }

    @Test
    public void testXmlSagaAndTransactedNestedSteps() throws Exception {
        context.getRegistry().bind("myPolicy", policy);
        context.addService(new InMemorySagaService());
        addXmlRoutes("""
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                  <route>
                    <from uri="direct:start"/>
                    <setBody><simple>${body}-a</simple></setBody>
                    <saga>
                      <setBody><simple>${body}-b</simple></setBody>
                    </saga>
                    <setBody><simple>${body}-c</simple></setBody>
                    <transacted ref="myPolicy">
                      <setBody><simple>${body}-d</simple></setBody>
                    </transacted>
                    <setBody><simple>${body}-e</simple></setBody>
                    <to uri="mock:result"/>
                  </route>
                </routes>
                """);

        assertOrder(TransactedDefinition.class, "x-a-b-c-d-e");
    }

    private void addXmlRoutes(String xml) throws Exception {
        List<RouteDefinition> routes
                = new ModelParser(new StringReader(xml)).parseRoutesDefinition().orElseThrow().getRoutes();
        ((ModelCamelContext) context).addRouteDefinitions(routes);
    }

    private void assertOrder(Class<?> wrapper, String expectedBody) throws Exception {
        context.start();

        // the saga or transacted wraps the entire route
        List<ProcessorDefinition<?>> outputs = ((ModelCamelContext) context).getRouteDefinitions().get(0).getOutputs();
        assertThat(outputs).hasSize(1);
        assertThat(outputs.get(0)).isInstanceOf(wrapper);

        getMockEndpoint("mock:result").expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start", "x");

        assertMockEndpointsSatisfied();
    }
}
