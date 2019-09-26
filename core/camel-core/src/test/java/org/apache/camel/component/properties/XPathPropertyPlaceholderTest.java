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
package org.apache.camel.component.properties;

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.Test;

public class XPathPropertyPlaceholderTest extends ContextTestSupport {

    @Test
    public void testFilter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:output-filter");
        mock.expectedMessageCount(1);

        template.sendBody("direct:filter", "<greeting><text>Hello, world!</text></greeting>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testChoice() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:output-choice");
        mock.expectedMessageCount(1);

        template.sendBody("direct:choice", "<greeting><text>Bye, world!</text></greeting>");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testChoice2() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:output-choice2");
        mock.expectedMessageCount(1);

        template.sendBody("direct:choice2", "<greeting><text>Bye, world!</text></greeting>");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                Properties prop = new Properties();
                prop.put("foo", "//greeting/text = 'Hello, world!'");
                prop.put("bar", "//greeting/text = 'Bye, world!'");

                PropertiesComponent pc = context.getPropertiesComponent();
                pc.setInitialProperties(prop);

                from("direct:filter").filter().xpath("{{foo}}").log("Passed filter!").to("mock:output-filter");

                from("direct:choice").choice().when(xpath("{{bar}}")).log("Passed choice!").to("mock:output-choice");

                from("direct:choice2").choice().when().xpath("{{bar}}").log("Passed choice2!").to("mock:output-choice2");
            }
        };
    }

}
