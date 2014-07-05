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
package org.apache.camel.dataformat.bindy.classtype;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.model.simple.oneclass.Order;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 *
 */
public class BindyCsvClassTypeAsStringTest extends CamelTestSupport {

    @Test
    public void testMarshallMessage() throws Exception {
        String expected = "1,B2,Keira,Knightley,ISIN,XX23456789,BUY,Share,400.25,EUR,14-01-2009,11-02-2010 23:21:59\r\n";

        getMockEndpoint("mock:in").expectedBodiesReceived(expected);

        template.sendBody("direct:in", generateOrder());

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnmarshallMessage() throws Exception {
        String data = "1,B2,Keira,Knightley,ISIN,XX23456789,BUY,Share,400.25,EUR,14-01-2009,03-02-2010 23:21:59\r\n";

        getMockEndpoint("mock:out").expectedMessageCount(1);
        getMockEndpoint("mock:out").message(0).body().isInstanceOf(Order.class);

        template.sendBody("direct:out", data);

        assertMockEndpointsSatisfied();

        Order order = getMockEndpoint("mock:out").getReceivedExchanges().get(0).getIn().getBody(Order.class);
        assertEquals(1, order.getOrderNr());
        assertEquals("BUY", order.getOrderType());
        assertEquals("B2", order.getClientNr());
        assertEquals("Keira", order.getFirstName());
        assertEquals("Knightley", order.getLastName());
        assertEquals(new BigDecimal("400.25"), order.getAmount());
        assertEquals("ISIN", order.getInstrumentCode());
        assertEquals("XX23456789", order.getInstrumentNumber());
        assertEquals("Share", order.getInstrumentType());
        assertEquals("EUR", order.getCurrency());

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        // 4 hour shift
        // 03-02-2010 23:21:59 by GMT+4
        calendar.set(2010, 1, 3, 19, 21, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        assertEquals(calendar.getTime(), order.getOrderDateTime());
    }

    public Order generateOrder() {
        Order order = new Order();
        order.setOrderNr(1);
        order.setOrderType("BUY");
        order.setClientNr("B2");
        order.setFirstName("Keira");
        order.setLastName("Knightley");
        order.setAmount(new BigDecimal("400.25"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumber("XX23456789");
        order.setInstrumentType("Share");
        order.setCurrency("EUR");

        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 0, 14);
        order.setOrderDate(calendar.getTime());

        calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        // 4 hour shift
        // 11-02-2010 23:21:59 by GMT+4
        calendar.set(2010, 1, 11, 19, 21, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        order.setOrderDateTime(calendar.getTime());

        return order;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat();
                bindy.setClassType(org.apache.camel.dataformat.bindy.model.simple.oneclass.Order.class);
                bindy.setLocale("en");
                bindy.setType(BindyType.Csv);

                from("direct:in")
                    .marshal(bindy)
                    .to("mock:in");

                from("direct:out")
                    .unmarshal().bindy(BindyType.Csv, org.apache.camel.dataformat.bindy.model.simple.oneclass.Order.class)
                    .to("mock:out");

            }
        };
    }
}
