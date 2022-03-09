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

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ChoiceInPreconditionModeTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testRed() throws Exception {
        Properties init = new Properties();
        init.setProperty("red", "true");
        init.setProperty("blue", "false");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        getMockEndpoint("mock:red").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Red");

        assertMockEndpointsSatisfied();

        // doSwitch is optimized and we only have the selected node
        Assertions.assertNull(context.getProcessor("mySwitch"));
        Assertions.assertNotNull(context.getProcessor("myRed"));
        Assertions.assertNull(context.getProcessor("myBlue"));
        Assertions.assertNull(context.getProcessor("myYellow"));
    }

    @Test
    void testBlue() throws Exception {
        Properties init = new Properties();
        init.setProperty("red", "false");
        init.setProperty("blue", "true");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        getMockEndpoint("mock:blue").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Blue");

        assertMockEndpointsSatisfied();

        // doSwitch is optimized and we only have the selected node
        Assertions.assertNull(context.getProcessor("mySwitch"));
        Assertions.assertNull(context.getProcessor("myRed"));
        Assertions.assertNotNull(context.getProcessor("myBlue"));
        Assertions.assertNull(context.getProcessor("myYellow"));
    }

    @Test
    void testYellow() throws Exception {
        Properties init = new Properties();
        init.setProperty("red", "false");
        init.setProperty("blue", "false");
        context.getPropertiesComponent().setInitialProperties(init);

        context.addRoutes(createRouteBuilder());
        context.start();

        getMockEndpoint("mock:yellow").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Yellow");

        assertMockEndpointsSatisfied();

        // doSwitch is optimized and we only have the selected node
        Assertions.assertNull(context.getProcessor("mySwitch"));
        Assertions.assertNull(context.getProcessor("myRed"));
        Assertions.assertNull(context.getProcessor("myBlue"));
        Assertions.assertNotNull(context.getProcessor("myYellow"));
    }

    @Test
    void testNone() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute")
                    .choice().precondition().id("mySwitch")
                        .when(simple("{{?red}}")).to("mock:red").id("myRed")
                        .when(simple("{{?blue}}")).to("mock:blue").id("myBlue")
                    .end()
                    .to("mock:end");
            }
        });
        context.start();

        getMockEndpoint("mock:end").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello Yellow");

        assertMockEndpointsSatisfied();

        // doSwitch is optimized and we only have the selected node
        Assertions.assertNull(context.getProcessor("mySwitch"));
        Assertions.assertNull(context.getProcessor("myRed"));
        Assertions.assertNull(context.getProcessor("myBlue"));
        Assertions.assertNull(context.getProcessor("myYellow"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").routeId("myRoute")
                        .choice().precondition(true).id("mySwitch")
                        .when(simple("{{red}}")).to("mock:red").id("myRed")
                        .when(simple("{{blue}}")).to("mock:blue").id("myBlue")
                        .otherwise().to("mock:yellow").id("myYellow");
            }
        };
    }

}
