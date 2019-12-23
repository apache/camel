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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Test;

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

        List<String> camelRobotVariables = ObjectHelper.cast(List.class, responseExchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_VARIABLES));
        for (String camelRobotVariable : camelRobotVariables) {
            if (!camelRobotVariable.contains("headers") && !camelRobotVariable.contains("properties") && camelRobotVariable.contains("body")) {
                assertEquals("Body variable content should be [body:<body_value>]", "body:Hello Robot", camelRobotVariable);
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("stringKey")) {
                assertEquals("Header variable content should be [headers.stringKey:<header_value>]", "headers.stringKey:str1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("numericIntKey")) {
                assertEquals("Header variable content should be [headers.numericIntKey:<header_value>]", "headers.numericIntKey:1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("numericBigDecimalKey")) {
                assertEquals("Header variable content should be [headers.numericIntKey:<header_value>]", "headers.numericBigDecimalKey:2", camelRobotVariable);
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("innerStringKey")) {
                assertEquals("Header variable content should be [headers.stringKey:<header_value>]", "headers.inner.innerStringKey:str1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("innerNumericIntKey")) {
                assertEquals("Header variable content should be [headers.numericIntKey:<header_value>]", "headers.inner.innerNumericIntKey:1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("headers") && camelRobotVariable.contains("innerNumericBigDecimalKey")) {
                assertEquals("Header variable content should be [headers.numericIntKey:<header_value>]", "headers.inner.innerNumericBigDecimalKey:2", camelRobotVariable);
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("stringKey")) {
                assertEquals("Header variable content should be [properties.stringKey:<header_value>]", "properties.stringKey:str1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("numericIntKey")) {
                assertEquals("Header variable content should be [properties.numericIntKey:<header_value>]", "properties.numericIntKey:1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("numericBigDecimalKey")) {
                assertEquals("Header variable content should be [properties.numericIntKey:<header_value>]", "properties.numericBigDecimalKey:2", camelRobotVariable);
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("innerStringKey")) {
                assertEquals("Header variable content should be [properties.stringKey:<header_value>]", "properties.inner.innerStringKey:str1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("innerNumericIntKey")) {
                assertEquals("Header variable content should be [properties.numericIntKey:<header_value>]", "properties.inner.innerNumericIntKey:1", camelRobotVariable);
            }
            if (camelRobotVariable.contains("properties") && camelRobotVariable.contains("innerNumericBigDecimalKey")) {
                assertEquals("Header variable content should be [properties.numericIntKey:<header_value>]", "properties.inner.innerNumericBigDecimalKey:2", camelRobotVariable);
            }
        }

        assertMockEndpointsSatisfied();
        
        Exchange exchange = mock.getExchanges().get(0);
        assertTrue(ObjectHelper.cast(Integer.class,
                exchange.getIn().getHeader(RobotFrameworkCamelConstants.CAMEL_ROBOT_RETURN_CODE)) == 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:setVariableCamelExchange").to("robotframework:src/test/resources/org/apache/camel/component/robotframework/set_variable_camel_exchange.robot?xunitFile=target/out.xml")
                    .to("mock:result");
            }
        };
    }
}
