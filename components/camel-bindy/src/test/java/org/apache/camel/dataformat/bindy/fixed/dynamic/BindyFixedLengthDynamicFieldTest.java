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
package org.apache.camel.dataformat.bindy.fixed.dynamic;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.annotation.DataField;
import org.apache.camel.dataformat.bindy.annotation.FixedLengthRecord;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * This test validates the marshalling / unmarshalling of a fixed-length data field for which the length of the
 * field is defined by the value of another field in the record.
 */
public class BindyFixedLengthDynamicFieldTest extends CamelTestSupport {

    public static final String URI_DIRECT_MARSHALL         = "direct:marshall";
    public static final String URI_DIRECT_UNMARSHALL       = "direct:unmarshall";
    public static final String URI_MOCK_MARSHALL_RESULT    = "mock:marshall-result";
    public static final String URI_MOCK_UNMARSHALL_RESULT  = "mock:unmarshall-result";
    
    private static final String TEST_RECORD = "10A9Pauline^M^ISIN10XD12345678BUYShare000002500.45USD01-08-2009\r\n";
    private static final String TEST_RECORD_WITH_EXTRA_CHARS = "10A9Pauline^M^ISIN10XD12345678BUYShare000002500.45USD01-08-2009x\r\n";

    @EndpointInject(URI_MOCK_MARSHALL_RESULT)
    private MockEndpoint marshallResult;

    @EndpointInject(URI_MOCK_UNMARSHALL_RESULT)
    private MockEndpoint unmarshallResult;

    // *************************************************************************
    // TESTS
    // *************************************************************************
    
    @Test
    public void testUnmarshallMessage() throws Exception {

        unmarshallResult.expectedMessageCount(1);
        template.sendBody(URI_DIRECT_UNMARSHALL, TEST_RECORD);
        
        unmarshallResult.assertIsSatisfied();

        // check the model
        BindyFixedLengthDynamicFieldTest.Order order = 
            (BindyFixedLengthDynamicFieldTest.Order) unmarshallResult.getReceivedExchanges().get(0).getIn().getBody();
        assertEquals(10, order.getOrderNr());
        // the field is not trimmed
        assertEquals("Pauline", order.getFirstName());
        assertEquals("M", order.getLastName());
        assertEquals("XD12345678", order.getInstrumentNumber());
    }
    
    @Test
    public void testFailWhenUnmarshallMessageWithUnmappedChars() throws Exception {

        unmarshallResult.reset();
        unmarshallResult.expectedMessageCount(0);
        try {
            template.sendBody(URI_DIRECT_UNMARSHALL, TEST_RECORD_WITH_EXTRA_CHARS);
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
            assertTrue(e.getCause().getMessage().contains("unmapped characters"));
            return;
        }
        
        fail("An error is expected when unmapped characters are encountered in the fixed length record");
    }
    
    @Test
    public void testMarshallMessage() throws Exception {
        BindyFixedLengthDynamicFieldTest.Order order = new Order();
        order.setOrderNr(10);
        order.setOrderType("BUY");
        order.setClientNr("A9");
        order.setFirstName("Pauline");
        order.setLastName("M");
        order.setAmount(new BigDecimal("2500.45"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumberLen(10);
        order.setInstrumentNumber("XD12345678");
        order.setInstrumentType("Share");
        order.setCurrency("USD");
        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1);
        order.setOrderDate(calendar.getTime());
        
        marshallResult.expectedMessageCount(1);
        marshallResult.expectedBodiesReceived(Arrays.asList(new String[] {TEST_RECORD}));
        template.sendBody(URI_DIRECT_MARSHALL, order);
        marshallResult.assertIsSatisfied();
    }
    
    // *************************************************************************
    // ROUTES
    // *************************************************************************
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat()
                        .locale("en")
                        .classType(BindyFixedLengthDynamicFieldTest.Order.class)
                        .fixed();

                from(URI_DIRECT_MARSHALL)
                    .marshal(bindy)
                    .to(URI_MOCK_MARSHALL_RESULT);
            
                from(URI_DIRECT_UNMARSHALL)
                    .unmarshal().bindy(BindyType.Fixed, BindyFixedLengthDynamicFieldTest.Order.class)
                    .to(URI_MOCK_UNMARSHALL_RESULT);
            }
        };
        
        return routeBuilder;
    }

    // *************************************************************************
    // DATA MODEL
    // *************************************************************************
    @FixedLengthRecord()
    public static class Order {

        @DataField(pos = 1, length = 2)
        private int orderNr;

        @DataField(pos = 2, length = 2)
        private String clientNr;

        @DataField(pos = 3, delimiter = "^")
        private String firstName;

        @DataField(pos = 4, delimiter = "^")
        private String lastName;

        @DataField(pos = 5, length = 4)
        private String instrumentCode;

        @DataField(pos = 6, length = 2, align = "R", paddingChar = '0')
        private int instrumentNumberLen;
        
        @DataField(pos = 7, length = 10)
        private String instrumentNumber;

        @DataField(pos = 8, length = 3)
        private String orderType;

        @DataField(pos = 9, length = 5)
        private String instrumentType;

        @DataField(pos = 10, precision = 2, length = 12, paddingChar = '0')
        private BigDecimal amount;

        @DataField(pos = 11, length = 3)
        private String currency;

        @DataField(pos = 12, length = 10, pattern = "dd-MM-yyyy")
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
        
        public void setInstrumentNumberLen(int instrumentNumberLen) {
            this.instrumentNumberLen = instrumentNumberLen;
        }

        public int getInstrumentNumberLen() {
            return instrumentNumberLen;
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
