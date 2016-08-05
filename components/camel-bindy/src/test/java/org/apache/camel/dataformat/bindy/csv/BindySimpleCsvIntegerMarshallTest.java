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

import java.math.BigDecimal;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.CsvRecord;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class BindySimpleCsvIntegerMarshallTest extends AbstractJUnit4SpringContextTests {

    private static final String URI_MOCK_RESULT = "mock:result";
    private static final String URI_DIRECT_START = "direct:start";

    @Produce(uri = URI_DIRECT_START)
    private ProducerTemplate template;

    @EndpointInject(uri = URI_MOCK_RESULT)
    private MockEndpoint result;

    @Test
    @DirtiesContext
    public void testMarshallMessage() throws Exception {
        MyOrder order = new MyOrder();
        order.setInstrument("XX23456789");
        order.setQuantity(12345678);
        order.setPrice(new BigDecimal("400.25"));

        result.expectedBodiesReceived("XX23456789,12345678,400.25\r\n");
        template.sendBody(order);
        result.assertIsSatisfied();
    }

    public static class ContextConfig extends RouteBuilder {
        public void configure() {
            BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat(MyOrder.class);
            camelDataFormat.setLocale("en_US");

            from(URI_DIRECT_START).marshal(camelDataFormat)
                .to(URI_MOCK_RESULT);
        }
    }

    @CsvRecord(separator = ",")
    public static class MyOrder {
        @DataField(pos = 1)
        private String instrument;

        @DataField(pos = 2)
        private int quantity;

        @DataField(pos = 9, precision = 2)
        private BigDecimal price;


        public String getInstrument() {
            return instrument;
        }

        public void setInstrument(String instrument) {
            this.instrument = instrument;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }

}
