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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RobotFrameworkCamelUtilsTest extends CamelTestSupport {

    private Exchange exchange;

    @Override
    protected void doPostSetup() throws Exception {
        super.doPostSetup();
        exchange = createExchangeWithBody("Hello Robot");
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateRobotVariablesFromCamelExchange() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        Map<String, Object> headers = new HashMap<>();
        headers.put("stringKey", "str1");
        headers.put("numericIntKey", 1);
        headers.put("numericBigDecimalKey", new BigDecimal(2));

        Map<String, Object> inner1 = new HashMap<>();
        inner1.put("innerStringKey", "str1");
        inner1.put("innerNumericIntKey", 1);
        inner1.put("innerNumericBigDecimalKey", new BigDecimal(2));

        headers.put("inner", inner1);

        exchange.getIn().setHeaders(headers);
        exchange.setProperty("stringKey", "str1");
        exchange.setProperty("numericIntKey", 1);
        exchange.setProperty("numericBigDecimalKey", new BigDecimal(2));
        exchange.setProperty("inner", inner1);

        Exchange responseExchange = template.send("direct:setVariableCamelExchange", exchange);

        List<String> camelRobotVariables = ObjectHelper.cast(List.class,
                responseExchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_VARIABLES));
        for (String camelRobotVariable : camelRobotVariables) {
            if (!camelRobotVariable.contains("headers") && !camelRobotVariable.contains("properties")
                    && camelRobotVariable.contains("body")) {
                assertEquals("body:Hello Robot", camelRobotVariable, "Body variable content should be [body:<body_value>]");
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("stringKey")) {
                assertEquals("headers.stringKey:str1", camelRobotVariable,
                        "Header variable content should be [headers.stringKey:<header_value>]");
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("numericIntKey")) {
                assertEquals("headers.numericIntKey:1", camelRobotVariable,
                        "Header variable content should be [headers.numericIntKey:<header_value>]");
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("numericBigDecimalKey")) {
                assertEquals("headers.numericBigDecimalKey:2", camelRobotVariable,
                        "Header variable content should be [headers.numericIntKey:<header_value>]");
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("innerStringKey")) {
                assertEquals("headers.inner.innerStringKey:str1", camelRobotVariable,
                        "Header variable content should be [headers.stringKey:<header_value>]");
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("innerNumericIntKey")) {
                assertEquals("headers.inner.innerNumericIntKey:1", camelRobotVariable,
                        "Header variable content should be [headers.numericIntKey:<header_value>]");
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("innerNumericBigDecimalKey")) {
                assertEquals("headers.inner.innerNumericBigDecimalKey:2", camelRobotVariable,
                        "Header variable content should be [headers.numericIntKey:<header_value>]");
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("stringKey")) {
                assertEquals("properties.stringKey:str1", camelRobotVariable,
                        "Header variable content should be [properties.stringKey:<header_value>]");
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("numericIntKey")) {
                assertEquals("properties.numericIntKey:1", camelRobotVariable,
                        "Header variable content should be [properties.numericIntKey:<header_value>]");
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("numericBigDecimalKey")) {
                assertEquals("properties.numericBigDecimalKey:2", camelRobotVariable,
                        "Header variable content should be [properties.numericIntKey:<header_value>]");
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("innerStringKey")) {
                assertEquals("properties.inner.innerStringKey:str1", camelRobotVariable,
                        "Header variable content should be [properties.stringKey:<header_value>]");
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("innerNumericIntKey")) {
                assertEquals("properties.inner.innerNumericIntKey:1", camelRobotVariable,
                        "Header variable content should be [properties.numericIntKey:<header_value>]");
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("innerNumericBigDecimalKey")) {
                assertEquals("properties.inner.innerNumericBigDecimalKey:2", camelRobotVariable,
                        "Header variable content should be [properties.numericIntKey:<header_value>]");
            }
        }

        MockEndpoint.assertIsSatisfied(context);

        Exchange exchange = mock.getExchanges().get(0);
        assertEquals(0, (int) ObjectHelper.cast(Integer.class,
                exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:setVariableCamelExchange")
                        .to("robotframework:src/test/resources/org/apache/camel/component/robotframework/set_variable_camel_exchange.robot?"
                            + "xunitFile=target/out.xml&outputDirectory=target&allowContextMapAll=true")
                        .to("mock:result");
            }
        };
    }
}
