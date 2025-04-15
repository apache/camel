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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NestedChoiceOtherwiseIssueTest extends ContextTestSupport {

    @Test
    public void testNestedChoiceOtherwise() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context,
                context.getRouteDefinition("myRoute"));
        assertNotNull(xml);
        log.info(xml);
        System.out.println(xml);

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(0);
        getMockEndpoint("mock:other2").expectedMessageCount(0);
        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 10);
        assertMockEndpointsSatisfied();

        resetMocks();
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:other2").expectedMessageCount(0);
        template.sendBodyAndHeader("direct:start", "Hello World", "bar", 11);
        assertMockEndpointsSatisfied();

        resetMocks();
        getMockEndpoint("mock:foo").expectedMessageCount(0);
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        getMockEndpoint("mock:other").expectedMessageCount(1);
        getMockEndpoint("mock:other2").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Hello World", "cheese", 12);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("myRoute")
                    .errorHandler(noErrorHandler())
                    .choice()
                        .when(header("foo"))
                            .to("mock:foo")
                        .otherwise()
                            .to("mock:other")
                            .choice()
                                .when(header("bar"))
                                    .to("mock:bar")
                                .endChoice("other2")
                                .otherwise()
                                    .to("mock:other2")
                                .end()
                            .end();
            }
        };
    }
}
