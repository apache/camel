/**
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
package org.apache.camel.dataformat.bindy.csv;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.simple.linkonetomany.Order;
import org.apache.camel.dataformat.bindy.model.simple.linkonetomany.OrderItem;
import org.apache.camel.processor.interceptor.Tracer;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindyMarshalOneToManyWithHeadersTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_MOCK_ERROR = "mock:error";
    private static final String URI_DIRECT_START = "direct:start";

    private Order order;
    private String expected;

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    @Test
    @DirtiesContext
    public void testMarshallMessage() throws Exception {
        expected = "orderNumber,customerName,sku,quantity,unitPrice\r\n"
                + "11111,Joe Blow,abc,1,3\r\n"
                + "11111,Joe Blow,cde,3,2\r\n";

        result.expectedBodiesReceived(expected);

        template.sendBody(generateModel());

        result.assertIsSatisfied();
    }

    public Order generateModel() {

        Order order = new Order();
        order.setCustomerName("Joe Blow");
        order.setOrderNumber(11111);

        OrderItem oi1 = new OrderItem();
        oi1.setSku("abc");
        oi1.setQuantity(1);
        oi1.setUnitPrice(3);

        OrderItem oi2 = new OrderItem();
        oi2.setSku("cde");
        oi2.setQuantity(3);
        oi2.setUnitPrice(2);

        List<OrderItem> orderList = Arrays.asList(oi1, oi2);
        order.setItems(orderList);

        return order;
    }

    public static class ContextConfig extends RouteBuilder {

        public void configure() {

            Tracer tracer = new Tracer();
            tracer.setLogLevel(LoggingLevel.INFO);
            tracer.setLogName("org.apache.camel.bindy");

            getContext().addInterceptStrategy(tracer);

            BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(Order.class);
            camelDataFormat.setLocale("en");

            // default should errors go to mock:error
            errorHandler(deadLetterChannel(URI_MOCK_ERROR).redeliveryDelay(0));

            onException(Exception.class).maximumRedeliveries(0).handled(true);

            from(URI_DIRECT_START).marshal(camelDataFormat).to(URI_MOCK_RESULT);
        }

    }

}
