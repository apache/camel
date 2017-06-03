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
package org.apache.camel.dataformat.bindy.fixed.skipfields;

import java.math.BigDecimal;
import java.util.Date;

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
 * This test validates that bindy can skip values in the fixed-length record when absolute pos
 * values are provided
 */
public class BindySimpleSkipFieldsTest extends CamelTestSupport {

    public static final String URI_DIRECT_MARSHALL         = "direct:marshall";
    public static final String URI_DIRECT_UNMARSHALL       = "direct:unmarshall";
    public static final String URI_MOCK_MARSHALL_RESULT    = "mock:marshall-result";
    public static final String URI_MOCK_UNMARSHALL_RESULT  = "mock:unmarshall-result";
    
    private static final String TEST_RECORD = "10A9  PaulineM    ISINXD12345678BUYShare000002500.45USD01-08-2009Hello     \r\n";

    @EndpointInject(uri = URI_MOCK_MARSHALL_RESULT)
    private MockEndpoint marshallResult;

    @EndpointInject(uri = URI_MOCK_UNMARSHALL_RESULT)
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
        BindySimpleSkipFieldsTest.Order order = 
            (BindySimpleSkipFieldsTest.Order) unmarshallResult.getReceivedExchanges().get(0).getIn().getBody();
        assertEquals(10, order.getOrderNr());
        // the field is not trimmed
        assertEquals(null, order.getFirstName());
        assertEquals("M    ", order.getLastName());
        assertEquals("Hello     ", order.getComment());
    }
    
    
    // *************************************************************************
    // ROUTES
    // *************************************************************************
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                BindyDataFormat bindy = new BindyDataFormat();
                bindy.setClassType(BindySimpleSkipFieldsTest.Order.class);
                bindy.setLocale("en");
                bindy.setType(BindyType.Fixed);

                from(URI_DIRECT_MARSHALL)
                    .marshal(bindy)
                    .to(URI_MOCK_MARSHALL_RESULT);
            
                from(URI_DIRECT_UNMARSHALL)
                    .unmarshal().bindy(BindyType.Fixed, BindySimpleSkipFieldsTest.Order.class)
                    .to(URI_MOCK_UNMARSHALL_RESULT);
            }
        };
        
        return routeBuilder;
    }

    // *************************************************************************
    // DATA MODEL
    // *************************************************************************
    @FixedLengthRecord(ignoreTrailingChars = false)
    public static class Order {

        @DataField(pos = 1, length = 2)
        private int orderNr;

        @DataField(pos = 3, length = 2)
        private String clientNr;

        // not annotated for this test
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

        @DataField(pos = 66, length = 10, align = "L", paddingChar = ' ')
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
