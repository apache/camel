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

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.CommonBindyTest;
import org.apache.camel.dataformat.bindy.model.simple.oneclassdifferentposition.Order;
import org.apache.camel.processor.interceptor.Tracer;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration
public class BindySimpleCsvMarshallPositionModifiedTest extends CommonBindyTest {

    private List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
    private String expected;

    @Test
    @DirtiesContext
    public void testReverseMessage() throws Exception {

        expected = "08-01-2009,EUR,400.25,Share,BUY,BE12345678,ISIN,Knightley,Keira,B2,1\r\n";
        result.expectedBodiesReceived(expected);

        template.sendBody(generateModel());
        result.assertIsSatisfied();
    }

    public List<Map<String, Object>> generateModel() {
        Map<String, Object> model = new HashMap<String, Object>();

        Order order = new Order();
        order.setOrderNr(1);
        order.setOrderType("BUY");
        order.setClientNr("B2");
        order.setFirstName("Keira");
        order.setLastName("Knightley");
        order.setAmount(new BigDecimal("400.25"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumber("BE12345678");
        order.setInstrumentType("Share");
        order.setCurrency("EUR");

        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 0, 8);
        order.setOrderDate(calendar.getTime());

        model.put(order.getClass().getName(), order);

        models.add(model);

        return models;
    }

    public static class ContextConfig extends RouteBuilder {

        public void configure() {

            Tracer tracer = new Tracer();
            tracer.setLogLevel(LoggingLevel.ERROR);
            tracer.setLogName("org.apache.camel.bindy");

            getContext().addInterceptStrategy(tracer);

            BindyCsvDataFormat csvBindyDataFormat = new BindyCsvDataFormat(org.apache.camel.dataformat.bindy.model.simple.oneclassdifferentposition.Order.class);
            csvBindyDataFormat.setLocale("en");

            // default should errors go to mock:error
            errorHandler(deadLetterChannel(URI_MOCK_ERROR).redeliveryDelay(0));

            onException(Exception.class).maximumRedeliveries(0).handled(true);

            from(URI_DIRECT_START).marshal(csvBindyDataFormat).to(URI_MOCK_RESULT);
        }
        
    }

}
