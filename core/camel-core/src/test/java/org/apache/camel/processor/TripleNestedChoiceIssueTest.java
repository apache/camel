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

public class TripleNestedChoiceIssueTest extends ContextTestSupport {

    @Test
    public void testNestedChoiceVeryBig() throws Exception {
        getMockEndpoint("mock:low").expectedMessageCount(0);
        getMockEndpoint("mock:med").expectedMessageCount(0);
        getMockEndpoint("mock:big").expectedMessageCount(0);
        getMockEndpoint("mock:verybig").expectedMessageCount(1);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 20);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedChoiceBig() throws Exception {
        getMockEndpoint("mock:low").expectedMessageCount(0);
        getMockEndpoint("mock:med").expectedMessageCount(0);
        getMockEndpoint("mock:big").expectedMessageCount(1);
        getMockEndpoint("mock:verybig").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 10);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedChoiceMed() throws Exception {
        getMockEndpoint("mock:low").expectedMessageCount(0);
        getMockEndpoint("mock:med").expectedMessageCount(1);
        getMockEndpoint("mock:big").expectedMessageCount(0);
        getMockEndpoint("mock:verybig").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 4);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNestedChoiceLow() throws Exception {
        String xml = PluginHelper.getModelToXMLDumper(context).dumpModelAsXml(context,
                context.getRouteDefinition("route1"));
        assertNotNull(xml);
        log.info(xml);
        System.out.println(xml);

        getMockEndpoint("mock:low").expectedMessageCount(1);
        getMockEndpoint("mock:med").expectedMessageCount(0);
        getMockEndpoint("mock:big").expectedMessageCount(0);
        getMockEndpoint("mock:verybig").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", 1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").choice().when(header("foo").isGreaterThan(1)).choice().when(header("foo").isGreaterThan(5))
                        .choice().when(header("foo").isGreaterThan(10))
                        .to("mock:verybig").otherwise("big").to("mock:big").end().endChoice("med").otherwise("med").to("mock:med").end().endChoice("low")
                        .otherwise("low").to("mock:low").end();
            }
        };
    }
}
