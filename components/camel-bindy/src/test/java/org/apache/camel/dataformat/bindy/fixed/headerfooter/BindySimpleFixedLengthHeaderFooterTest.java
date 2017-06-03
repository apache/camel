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
package org.apache.camel.dataformat.bindy.fixed.headerfooter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.fixed.BindyFixedLengthDataFormat;
import org.apache.camel.model.dataformat.BindyDataFormat;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * This test validates that header and footer records are successfully marshalled / unmarshalled in conjunction
 * with the primary data records defined for the bindy data format.
 */
public class BindySimpleFixedLengthHeaderFooterTest extends CamelTestSupport {

    public static final String URI_DIRECT_MARSHALL               = "direct:marshall";
    public static final String URI_DIRECT_UNMARSHALL             = "direct:unmarshall";
    public static final String URI_MOCK_MARSHALL_RESULT          = "mock:marshall-result";
    public static final String URI_MOCK_UNMARSHALL_RESULT        = "mock:unmarshall-result";
    
    private static final String TEST_HEADER = "101-08-2009\r\n";
    private static final String TEST_RECORD = "10A9  PaulineM    ISINXD12345678BUYShare000002500.45USD01-08-2009\r\n";
    private static final String TEST_FOOTER = "9000000001\r\n";

    @EndpointInject(uri = URI_MOCK_MARSHALL_RESULT)
    private MockEndpoint marshallResult;

    @EndpointInject(uri = URI_MOCK_UNMARSHALL_RESULT)
    private MockEndpoint unmarshallResult;

    // *************************************************************************
    // TESTS
    // *************************************************************************
    
    @SuppressWarnings("unchecked")
    @Test
    public void testUnmarshallMessage() throws Exception {

        StringBuffer buff = new StringBuffer();
        buff.append(TEST_HEADER).append(TEST_RECORD).append(TEST_FOOTER);
        
        unmarshallResult.expectedMessageCount(1);
        
        template.sendBody(URI_DIRECT_UNMARSHALL, buff.toString());
        
        unmarshallResult.assertIsSatisfied();

        // check the model
        Exchange exchange = unmarshallResult.getReceivedExchanges().get(0);
        Order order = (Order) exchange.getIn().getBody();
        assertEquals(10, order.getOrderNr());
        // the field is not trimmed
        assertEquals("  Pauline", order.getFirstName());
        assertEquals("M    ", order.getLastName());
        
        Map<String, Object> receivedHeaderMap = 
            (Map<String, Object>) exchange.getIn().getHeader(BindyFixedLengthDataFormat.CAMEL_BINDY_FIXED_LENGTH_HEADER);
        
        Map<String, Object> receivedFooterMap = 
            (Map<String, Object>) exchange.getIn().getHeader(BindyFixedLengthDataFormat.CAMEL_BINDY_FIXED_LENGTH_FOOTER);
        
        assertNotNull(receivedHeaderMap);
        assertNotNull(receivedFooterMap);
        
        OrderHeader receivedHeader = (OrderHeader) receivedHeaderMap.get(OrderHeader.class.getName());
        OrderFooter receivedFooter = (OrderFooter) receivedFooterMap.get(OrderFooter.class.getName());
        
        assertNotNull(receivedHeader);
        assertNotNull(receivedFooter);
        
        OrderHeader expectedHeader = new OrderHeader();
        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1, 0, 0, 0);
        calendar.clear(Calendar.MILLISECOND);
        expectedHeader.setRecordDate(calendar.getTime());
        
        assertEquals(receivedHeader.getRecordType(), expectedHeader.getRecordType());   
        assertTrue(receivedHeader.getRecordDate().equals(expectedHeader.getRecordDate()));
    }
    
    /**
     * Verifies that header & footer provided as part of message body are marshalled successfully
     */
    @Test
    public void testMarshallMessageWithDirectHeaderAndFooterInput() throws Exception {
        Order order = new Order();
        order.setOrderNr(10);
        order.setOrderType("BUY");
        order.setClientNr("A9");
        order.setFirstName("Pauline");
        order.setLastName("M");
        order.setAmount(new BigDecimal("2500.45"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumber("XD12345678");
        order.setInstrumentType("Share");
        order.setCurrency("USD");
        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1, 0, 0, 0);
        order.setOrderDate(calendar.getTime());
        
        List<Map<String, Object>> input = new ArrayList<Map<String, Object>>();
        Map<String, Object> bodyRow = new HashMap<String, Object>();
        bodyRow.put(Order.class.getName(), order);
        input.add(createHeaderRow());
        input.add(bodyRow);
        input.add(createFooterRow());
        
        marshallResult.expectedMessageCount(1);
        StringBuffer buff = new StringBuffer();
        buff.append(TEST_HEADER).append(TEST_RECORD).append(TEST_FOOTER);
        marshallResult.expectedBodiesReceived(Arrays.asList(new String[] {buff.toString()}));
        template.sendBody(URI_DIRECT_MARSHALL, input);
        marshallResult.assertIsSatisfied();
    }
    
    /**
     * Verifies that header & footer provided via message headers are marshalled successfully
     */
    @Test
    public void testMarshallMessageWithIndirectHeaderAndFooterInput() throws Exception {
        Order order = new Order();
        order.setOrderNr(10);
        order.setOrderType("BUY");
        order.setClientNr("A9");
        order.setFirstName("Pauline");
        order.setLastName("M");
        order.setAmount(new BigDecimal("2500.45"));
        order.setInstrumentCode("ISIN");
        order.setInstrumentNumber("XD12345678");
        order.setInstrumentType("Share");
        order.setCurrency("USD");
        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1, 0, 0, 0);
        order.setOrderDate(calendar.getTime());
        
        List<Map<String, Object>> input = new ArrayList<Map<String, Object>>();
        Map<String, Object> bodyRow = new HashMap<String, Object>();
        bodyRow.put(Order.class.getName(), order);

        input.add(bodyRow);
        
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(BindyFixedLengthDataFormat.CAMEL_BINDY_FIXED_LENGTH_HEADER, createHeaderRow());
        headers.put(BindyFixedLengthDataFormat.CAMEL_BINDY_FIXED_LENGTH_FOOTER, createFooterRow());
        
        marshallResult.reset();
        marshallResult.expectedMessageCount(1);
        StringBuffer buff = new StringBuffer();
        buff.append(TEST_HEADER).append(TEST_RECORD).append(TEST_FOOTER);
        marshallResult.expectedBodiesReceived(Arrays.asList(new String[] {buff.toString()}));
        template.sendBodyAndHeaders(URI_DIRECT_MARSHALL, input, headers);
        marshallResult.assertIsSatisfied();
    }
        
    private Map<String, Object> createHeaderRow() {
        Map<String, Object> headerMap = new HashMap<String, Object>();
        OrderHeader header = new OrderHeader();
        Calendar calendar = new GregorianCalendar();
        calendar.set(2009, 7, 1, 0, 0, 0);
        header.setRecordDate(calendar.getTime());
        headerMap.put(OrderHeader.class.getName(), header);
        return headerMap;
    }
   
    private Map<String, Object> createFooterRow() {
        Map<String, Object> footerMap = new HashMap<String, Object>();
        OrderFooter footer = new OrderFooter();
        footer.setNumberOfRecordsInTheFile(1);
        footerMap.put(OrderFooter.class.getName(), footer);
        return footerMap;
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
                bindy.setClassType(Order.class);
                bindy.setLocale("en");
                bindy.setType(BindyType.Fixed);

                from(URI_DIRECT_MARSHALL)
                    .marshal(bindy)
                    .to(URI_MOCK_MARSHALL_RESULT);
            
                from(URI_DIRECT_UNMARSHALL)
                    .unmarshal().bindy(BindyType.Fixed, Order.class)
                    .to(URI_MOCK_UNMARSHALL_RESULT);
            }
        };
        
        return routeBuilder;
    }
}
