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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink.Client;
import org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink.Order;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.junit.Test;
import org.springframework.config.java.annotation.Bean;
import org.springframework.config.java.annotation.Configuration;
import org.springframework.config.java.test.JavaConfigContextLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(locations = "org.apache.camel.dataformat.bindy.csv.BindyComplexCsvMarshallTest$ContextConfig", loader = JavaConfigContextLoader.class)
public class BindyComplexCsvMarshallTest extends AbstractJUnit4SpringContextTests {

    private List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
    private String result = "10,A1,Julia,Roberts,BE123456789,Belgium Ventage 10/12,150,USD,14-01-2009";

    @Produce(uri = "direct:start")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Test
    public void testMarshallMessage() throws Exception {
        resultEndpoint.expectedBodiesReceived(result);

        template.sendBody(generateModel());

        resultEndpoint.assertIsSatisfied();
    }

    private List<Map<String, Object>> generateModel() {
        Map<String, Object> model = new HashMap<String, Object>();

        Order order = new Order();
        order.setOrderNr(10);
        order.setAmount(new BigDecimal("150"));
        order.setIsinCode("BE123456789");
        order.setInstrumentName("Belgium Ventage 10/12");
        order.setCurrency("USD");

        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 0, 14);
        order.setOrderDate(calendar.getTime());

        Client client = new Client();
        client.setClientNr("A1");
        client.setFirstName("Julia");
        client.setLastName("Roberts");

        order.setClient(client);

        model.put(order.getClass().getName(), order);
        model.put(client.getClass().getName(), client);

        models.add(0, model);

        return models;
    }

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {
        BindyCsvDataFormat camelDataFormat = new BindyCsvDataFormat("org.apache.camel.dataformat.bindy.model.complex.twoclassesandonelink");

        @Override
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").marshal(camelDataFormat).to("mock:result");
                }
            };
        }
    }

}
