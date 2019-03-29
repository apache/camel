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
package org.apache.camel.dataformat.bindy.fixed.marshall.simple;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BindySimpleFixedLengthMarshallWithClipTest extends CamelTestSupport {

    private List<Map<String, Object>> models = new ArrayList<>();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                BindyFixedLengthDataFormat bindy = new BindyFixedLengthDataFormat(Order.class);
                bindy.setLocale("en");

                from("direct:start")
                    .marshal(bindy)
                    .to("mock:result");
            }
        };
    }

    @Test
    public void testMarshallMessage() throws Exception {
        String expected = "10A9Madame deM    ISINXD12345678BUYShare000002500.45USD01-08-2009\r\n";
        getMockEndpoint("mock:result").expectedBodiesReceived(expected);

        template.sendBody("direct:start", generateModel());

        assertMockEndpointsSatisfied();
    }

    public List<Map<String, Object>> generateModel() {
        Map<String, Object> modelObjects = new HashMap<>();

        Order order = new Order();
        order.setOrderNr(10);
        order.setOrderType("BUY");
        order.setClientNr("A98");
        order.setFirstName("Madame de Sol");
        order.setLastName("M");
        order.setAmount(new BigDecimal("2500.45"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumber("XD12345678");
        order.setInstrumentType("Share");
        order.setCurrency("USD");

        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1);
        order.setOrderDate(calendar.getTime());

        modelObjects.put(order.getClass().getName(), order);

        models.add(modelObjects);

        return models;
    }

    @FixedLengthRecord(length = 65, paddingChar = ' ')
    public static class Order {

        @DataField(pos = 1, length = 2)
        private int orderNr;

        @DataField(pos = 3, length = 2, clip = true)
        private String clientNr;

        @DataField(pos = 5, length = 9, clip = true)
        private String firstName;

        @DataField(pos = 14, length = 5, align = "L")
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

        @Override
        public String toString() {
            return "Model : " + Order.class.getName() + " : " + this.orderNr + ", " + this.orderType + ", " + String.valueOf(this.amount) + ", " + this.instrumentCode + ", "
                    + this.instrumentNumber + ", " + this.instrumentType + ", " + this.currency + ", " + this.clientNr + ", " + this.firstName + ", " + this.lastName + ", "
                    + String.valueOf(this.orderDate);
        }
    }


}
