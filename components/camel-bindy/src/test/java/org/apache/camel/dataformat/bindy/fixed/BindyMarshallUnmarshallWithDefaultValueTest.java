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
package org.apache.camel.dataformat.bindy.fixed;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class BindyMarshallUnmarshallWithDefaultValueTest extends CamelTestSupport {

    @Test
    public void testUnMarshallMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultUnmarshal");
        template.sendBody("direct:unmarshal", "10A9              ISINXD12345678BUYShare000002500.45USD01-08-2009Hello     ");

        // check the model
        Order order = mock.getReceivedExchanges().get(0).getIn().getBody(Order.class);
        Assert.assertEquals(10, order.getOrderNr());
        // Default values are set
        Assert.assertEquals("John", order.getFirstName());
        Assert.assertEquals("Doe", order.getLastName());
        Assert.assertEquals("Hello     ", order.getComment());
    }
    
    @Test
    public void testUnMarshallMessageWithEol() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultUnmarshalEol");
        template.sendBody("direct:unmarshaleol", "10A9              ISINXD12345678BUYShare000002500.45USD01-08-2009Hello     QWERTY");

        // check the model
        OrderEol order = mock.getReceivedExchanges().get(0).getIn().getBody(OrderEol.class);
        Assert.assertEquals(10, order.getOrderNr());
        // Default values are set
        Assert.assertEquals("John", order.getFirstName());
        Assert.assertEquals("Doe", order.getLastName());
        Assert.assertEquals("Hello     ", order.getComment());
    }

    @Test
    public void testMarshallMessage() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultMarshal");

        mock.expectedBodiesReceived("10A9     JohnDoe  ISINXD12345678BUYShare000002500.45USD01-08-2009          \r\n");

        template.sendBody("direct:marshal", createOrder());
        mock.assertIsSatisfied();
    }
    
    @Test
    public void testMarshallMessageWithEol() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:resultMarshalEol");

        mock.expectedBodiesReceived("10A9     JohnDoe  ISINXD12345678BUYShare000002500.45USD01-08-2009          \r\n");

        template.sendBody("direct:marshaleol", createOrderEol());
        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal")
                    .marshal().bindy(BindyType.Fixed, Order.class)
                    .to("mock:resultMarshal");
                
                from("direct:marshaleol")
                    .marshal().bindy(BindyType.Fixed, OrderEol.class)
                    .to("mock:resultMarshalEol");
                
                from("direct:unmarshaleol")
                    .unmarshal().bindy(BindyType.Fixed, OrderEol.class)
                    .to("mock:resultUnmarshalEol");

                from("direct:unmarshal")
                    .unmarshal().bindy(BindyType.Fixed, Order.class)
                    .to("mock:resultUnmarshal");
            }
        };

    }

    private Order createOrder() {
        Order order = new Order();
        order.setOrderNr(10);
        order.setOrderType("BUY");
        order.setClientNr("A9");
        order.setAmount(new BigDecimal("2500.45"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumber("XD12345678");
        order.setInstrumentType("Share");
        order.setCurrency("USD");

        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1);
        order.setOrderDate(calendar.getTime());

        return order;
    }
    
    private OrderEol createOrderEol() {
        OrderEol order = new OrderEol();
        order.setOrderNr(10);
        order.setOrderType("BUY");
        order.setClientNr("A9");
        order.setAmount(new BigDecimal("2500.45"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumber("XD12345678");
        order.setInstrumentType("Share");
        order.setCurrency("USD");

        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1);
        order.setOrderDate(calendar.getTime());

        return order;
    }
    
    @FixedLengthRecord(length = 75)
    public static class Order  {

        @DataField(pos = 1, length = 2)
        private int orderNr;

        @DataField(pos = 3, length = 2)
        private String clientNr;

        @DataField(pos = 5, length = 9, defaultValue = "John", trim = true)
        private String firstName;

        @DataField(pos = 14, length = 5, align = "L", defaultValue = "Doe", trim = true)
        private String lastName;

        @DataField(pos = 19, length = 4)
        private String instrumentCode;

        @DataField(pos = 23, length = 10)
        private String instrumentNumber;

        @DataField(pos = 33, length = 3)
        private String orderType;

        @DataField(pos = 36, length = 5)
        private String instrumentType;

        @DataField(pos = 41, precision = 2, length = 12, paddingChar = '0')
        private BigDecimal amount;

        @DataField(pos = 53, length = 3)
        private String currency;

        @DataField(pos = 56, length = 10, pattern = "dd-MM-yyyy")
        private Date orderDate;

        @DataField(pos = 66, length = 10)
        private String comment;

        public int getOrderNr() {
            return orderNr;
        }

        public void setOrderNr(int orderNr) {
            this.orderNr = orderNr;
        }

        public String getClientNr() {
            return clientNr;
        }

        public void setClientNr(String clientNr) {
            this.clientNr = clientNr;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getInstrumentCode() {
            return instrumentCode;
        }

        public void setInstrumentCode(String instrumentCode) {
            this.instrumentCode = instrumentCode;
        }

        public String getInstrumentNumber() {
            return instrumentNumber;
        }

        public void setInstrumentNumber(String instrumentNumber) {
            this.instrumentNumber = instrumentNumber;
        }

        public String getOrderType() {
            return orderType;
        }

        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }

        public String getInstrumentType() {
            return instrumentType;
        }

        public void setInstrumentType(String instrumentType) {
            this.instrumentType = instrumentType;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Date getOrderDate() {
            return orderDate;
        }

        public void setOrderDate(Date orderDate) {
            this.orderDate = orderDate;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        @Override
        public String toString() {
            return "Model : " + Order.class.getName() + " : " + this.orderNr + ", " + this.orderType + ", " + String.valueOf(this.amount) + ", " + this.instrumentCode + ", "
                   + this.instrumentNumber + ", " + this.instrumentType + ", " + this.currency + ", " + this.clientNr + ", " + this.firstName + ", " + this.lastName + ", "
                   + String.valueOf(this.orderDate);
        }
    }
    
    @FixedLengthRecord(length = 75, eol = "QWERTY")
    public static class OrderEol  {

        @DataField(pos = 1, length = 2)
        private int orderNr;

        @DataField(pos = 3, length = 2)
        private String clientNr;

        @DataField(pos = 5, length = 9, defaultValue = "John", trim = true)
        private String firstName;

        @DataField(pos = 14, length = 5, align = "L", defaultValue = "Doe", trim = true)
        private String lastName;

        @DataField(pos = 19, length = 4)
        private String instrumentCode;

        @DataField(pos = 23, length = 10)
        private String instrumentNumber;

        @DataField(pos = 33, length = 3)
        private String orderType;

        @DataField(pos = 36, length = 5)
        private String instrumentType;

        @DataField(pos = 41, precision = 2, length = 12, paddingChar = '0')
        private BigDecimal amount;

        @DataField(pos = 53, length = 3)
        private String currency;

        @DataField(pos = 56, length = 10, pattern = "dd-MM-yyyy")
        private Date orderDate;

        @DataField(pos = 66, length = 10)
        private String comment;

        public int getOrderNr() {
            return orderNr;
        }

        public void setOrderNr(int orderNr) {
            this.orderNr = orderNr;
        }

        public String getClientNr() {
            return clientNr;
        }

        public void setClientNr(String clientNr) {
            this.clientNr = clientNr;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getInstrumentCode() {
            return instrumentCode;
        }

        public void setInstrumentCode(String instrumentCode) {
            this.instrumentCode = instrumentCode;
        }

        public String getInstrumentNumber() {
            return instrumentNumber;
        }

        public void setInstrumentNumber(String instrumentNumber) {
            this.instrumentNumber = instrumentNumber;
        }

        public String getOrderType() {
            return orderType;
        }

        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }

        public String getInstrumentType() {
            return instrumentType;
        }

        public void setInstrumentType(String instrumentType) {
            this.instrumentType = instrumentType;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Date getOrderDate() {
            return orderDate;
        }

        public void setOrderDate(Date orderDate) {
            this.orderDate = orderDate;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        @Override
        public String toString() {
            return "Model : " + Order.class.getName() + " : " + this.orderNr + ", " + this.orderType + ", " + String.valueOf(this.amount) + ", " + this.instrumentCode + ", "
                   + this.instrumentNumber + ", " + this.instrumentType + ", " + this.currency + ", " + this.clientNr + ", " + this.firstName + ", " + this.lastName + ", "
                   + String.valueOf(this.orderDate);
        }
    }

}
