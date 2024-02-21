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
package org.apache.camel.component.robotframework;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RobotFrameworkComponentTest extends CamelTestSupport {

    @Test
    public void testRobotFrameworkCamelBodyAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        MockEndpoint mockString = getMockEndpoint("mock:resultString");
        mock.expectedMinimumMessageCount(1);
        mockString.expectedMinimumMessageCount(1);

        template.sendBody("direct:setVariableCamelBody", "Hello Robot");
        template.sendBody("direct:assertRobotCamelInputAsString", "Hello Robot");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
        Exchange exchangeString = mockString.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchangeString.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
    }

    @Test
    public void testRobotFrameworkCamelBodyAsNumeric() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        MockEndpoint mockNumeric = getMockEndpoint("mock:resultNumeric");
        mock.expectedMinimumMessageCount(1);
        mockNumeric.expectedMinimumMessageCount(1);

        template.sendBody("direct:setVariableCamelBody", 1);
        template.sendBody("direct:assertRobotCamelInputAsNumeric", 1);

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
        Exchange exchangeNumeric = mockNumeric.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchangeNumeric.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
    }

    @Test
    public void testRobotFrameworkCamelBodyAndHeaderAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultHeader");
        mock.expectedMinimumMessageCount(1);

        template.sendBodyAndHeader("direct:setVariableCamelBodyAndHeader", "Hello Robot", "stringKey", "headerValue");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
    }

    @Test
    public void testRobotFrameworkCamelBodyAndPropertyAsString() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultProperty");
        mock.expectedMinimumMessageCount(1);

        template.sendBodyAndProperty("direct:setVariableCamelBodyAndProperty", "Hello Robot", "stringKey", "propertyValue");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
    }

    @Test
    public void testRobotFrameworkResourceUriHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultResourceUri");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:setVariableCamelBodyResourceUri", "Hello Robot");

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                RobotFrameworkComponent rf = context.getComponent("robotframework", RobotFrameworkComponent.class);
                rf.getConfiguration().setOutputDirectory("target");

                from("direct:setVariableCamelBody").to(
                        "robotframework:src/test/resources/org/apache/camel/component/robotframework/set_variable_camel_body.robot?xunitFile=target/out.xml")
                        .to("mock:result");

                from("direct:assertRobotCamelInputAsString")
                        .to("robotframework:src/test/resources/org/apache/camel/component/robotframework/assert_string_robot_with_camel_exchange_value_as_string.robot?xunitFile=target/out.xml")
                        .to("mock:resultString");

                from("direct:assertRobotCamelInputAsNumeric")
                        .to("robotframework:src/test/resources/org/apache/camel/component/robotframework/assert_string_robot_with_camel_exchange_value_as_numeric.robot?xunitFile=target/out.xml")
                        .to("mock:resultNumeric");

                from("direct:setVariableCamelBodyAndHeader").to(
                        "robotframework:src/test/resources/org/apache/camel/component/robotframework/set_variable_camel_header.robot?xunitFile=target/out.xml")
                        .to("mock:resultHeader");

                from("direct:setVariableCamelBodyAndProperty").to(
                        "robotframework:src/test/resources/org/apache/camel/component/robotframework/set_variable_camel_property.robot?xunitFile=target/out.xml&allowContextMapAll=true")
                        .to("mock:resultProperty");

                from("direct:setVariableCamelBodyResourceUri")
                        .setHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RESOURCE_URI)
                        .constant("src/test/resources/org/apache/camel/component/robotframework/set_variable_camel_body.robot")
                        .to("robotframework:dummy?xunitFile=target/out.xml&allowTemplateFromHeader=true")
                        .to("mock:resultResourceUri");
            }
        };
    }
}
