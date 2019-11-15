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
package org.apache.camel.dataformat.bindy.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.dataformat.bindy.model.fix.withoutsection.Order;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration
public class BindySimpleKeyValuePairWithoutSectionMarshallDslTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_MOCK_ERROR = "mock:error";
    private static final String URI_DIRECT_START = "direct:start";

    private List<Map<String, Object>> models = new ArrayList<>();

    @Produce(URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(URI_MOCK_RESULT)
    private MockEndpoint result;

    @EndpointInject(URI_MOCK_ERROR)
    private MockEndpoint error;

    @Test
    public void testMarshallWithoutSection() throws Exception {

        template.sendBody(generateModel());

        // We don't expect to have a message as an error will be raised
        result.expectedMessageCount(0);

        // Message has been delivered to the mock error
        error.expectedMessageCount(1);

        result.assertIsSatisfied();
        error.assertIsSatisfied();

        // and check that we have the caused exception stored
        Exchange exch = error.getReceivedExchanges().get(0);
        Exception cause = exch.getProperty(Exchange.EXCEPTION_CAUGHT, IllegalArgumentException.class);
        assertNotNull(cause);
        assertEquals("@Section and/or @KeyValuePairDataField have not been defined", cause.getMessage());
    }

    public List<Map<String, Object>> generateModel() {
        Map<String, Object> modelObjects = new HashMap<>();

        Order order = new Order();
        order.setAccount("BE.CHM.001");
        order.setClOrdId("CHM0001-01");
        order.setIDSource("4");
        order.setSecurityId("BE0001245678");
        order.setSide("1");
        order.setText("this is a camel - bindy test");

        modelObjects.put(order.getClass().getName(), order);

        models.add(modelObjects);
        return models;
    }

    public static class ContextConfig extends RouteBuilder {

        BindyKeyValuePairDataFormat orderBindyDataFormat = new BindyKeyValuePairDataFormat(org.apache.camel.dataformat.bindy.model.fix.withoutsection.Order.class);

        @Override
        public void configure() {

            // default should errors go to mock:error
            errorHandler(deadLetterChannel(URI_MOCK_ERROR));

            onException(IllegalArgumentException.class).maximumRedeliveries(0).handled(true);

            from(URI_DIRECT_START).marshal(orderBindyDataFormat).to(URI_MOCK_RESULT);
        }

    }
}
