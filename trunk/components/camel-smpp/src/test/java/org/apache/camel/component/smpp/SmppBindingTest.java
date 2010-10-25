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
package org.apache.camel.component.smpp;

import java.io.UnsupportedEncodingException;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.util.DeliveryReceiptState;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppBinding</code>
 * 
 * @version $Revision$
 * @author muellerc
 */
public class SmppBindingTest {
    
    private SmppBinding binding;

    @Before
    public void setUp() {
        binding = new SmppBinding() {
            Date getCurrentDate() {
                return new Date(1251666387000L);
            }
        };
    }

    @Test
    public void emptyConstructorShouldSetTheSmppConfiguration() {
        assertNotNull(binding.getConfiguration());
    }

    @Test
    public void constructorSmppConfigurationShouldSetTheSmppConfiguration() {
        SmppConfiguration configuration = new SmppConfiguration();
        binding = new SmppBinding(configuration);
        
        assertSame(configuration, binding.getConfiguration());
    }

    @Test
    public void createSubmitSmShouldCreateASubmitSmFromDefaults() throws UnsupportedEncodingException {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("Hello SMPP world!");
        SubmitSm submitSm = binding.createSubmitSm(exchange);
        
        assertEquals("Hello SMPP world!", new String(submitSm.getShortMessage()));
        assertEquals("1717", submitSm.getDestAddress());
        assertEquals(0x00, submitSm.getDestAddrNpi());
        assertEquals(0x00, submitSm.getDestAddrTon());
        assertEquals(0x01, submitSm.getPriorityFlag());
        assertEquals(0x00, submitSm.getProtocolId());
        assertEquals(0x01, submitSm.getRegisteredDelivery());
        assertEquals(0x00, submitSm.getReplaceIfPresent());
        // To avoid the test failure when running in different TimeZone
        //assertEquals("090830230627004+", submitSm.getScheduleDeliveryTime());
        assertEquals("CMT", submitSm.getServiceType());
        assertEquals("1616", submitSm.getSourceAddr());
        assertEquals(0x00, submitSm.getSourceAddrNpi());
        assertEquals(0x00, submitSm.getSourceAddrTon());
        assertNull(submitSm.getValidityPeriod());
        // not relevant
        //assertEquals(0, submitSm.getCommandId());
        //assertEquals(0, submitSm.getCommandStatus());
        //assertEquals(0, submitSm.getSequenceNumber());
    }

    @Test
    public void createSubmitSmWithDifferentEncoding() throws UnsupportedEncodingException {
        binding.getConfiguration().setEncoding("UTF-16");
        
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("Hello SMPP world!");
        SubmitSm submitSm = binding.createSubmitSm(exchange);

        assertArrayEquals("Hello SMPP world!".getBytes("UTF-16"), submitSm.getShortMessage());
    }

    @Test
    public void createSubmitSmShouldCreateASubmitSmFromHeaders() throws UnsupportedEncodingException {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setBody("Hello SMPP world!");
        exchange.getIn().setHeader(SmppBinding.DEST_ADDR, "1919");
        exchange.getIn().setHeader(SmppBinding.DEST_ADDR_NPI, NumberingPlanIndicator.NATIONAL.value());
        exchange.getIn().setHeader(SmppBinding.DEST_ADDR_TON, TypeOfNumber.NATIONAL.value());
        exchange.getIn().setHeader(SmppBinding.PRIORITY_FLAG, (byte) 0);
        exchange.getIn().setHeader(SmppBinding.PROTOCOL_ID, (byte) 1);
        exchange.getIn().setHeader(SmppBinding.REGISTERED_DELIVERY, (byte) 0);
        exchange.getIn().setHeader(SmppBinding.REPLACE_IF_PRESENT_FLAG, (byte) 1);
        exchange.getIn().setHeader(SmppBinding.SCHEDULE_DELIVERY_TIME, new Date(1251753000000L));
        exchange.getIn().setHeader(SmppBinding.SERVICE_TYPE, "XXX");
        exchange.getIn().setHeader(SmppBinding.VALIDITY_PERIOD, new Date(1251753600000L));
        exchange.getIn().setHeader(SmppBinding.SOURCE_ADDR, "1818");
        exchange.getIn().setHeader(SmppBinding.SOURCE_ADDR_NPI, NumberingPlanIndicator.NATIONAL.value());
        exchange.getIn().setHeader(SmppBinding.SOURCE_ADDR_TON, TypeOfNumber.NATIONAL.value());
        SubmitSm submitSm = binding.createSubmitSm(exchange);
        
        assertEquals("Hello SMPP world!", new String(submitSm.getShortMessage()));
        assertEquals("1919", submitSm.getDestAddress());
        assertEquals(0x08, submitSm.getDestAddrNpi());
        assertEquals(0x02, submitSm.getDestAddrTon());
        assertEquals(0x00, submitSm.getPriorityFlag());
        assertEquals(0x01, submitSm.getProtocolId());
        assertEquals(0x00, submitSm.getRegisteredDelivery());
        assertEquals(0x01, submitSm.getReplaceIfPresent());
        // To avoid the test failure when running in different TimeZone
        //assertEquals("090831231000004+", submitSm.getScheduleDeliveryTime());
        assertEquals("XXX", submitSm.getServiceType());
        assertEquals("1818", submitSm.getSourceAddr());
        assertEquals(0x08, submitSm.getSourceAddrNpi());
        assertEquals(0x02, submitSm.getSourceAddrTon());
        //assertEquals("090831232000004+", submitSm.getValidityPeriod());
        // not relevant
        //assertEquals(0, submitSm.getCommandId());
        //assertEquals(0, submitSm.getCommandStatus());
        //assertEquals(0, submitSm.getSequenceNumber());
    }

    @Test
    public void createSmppMessageFromAlertNotificationShouldReturnASmppMessage() {
        AlertNotification alertNotification = new AlertNotification();
        alertNotification.setCommandId(1);
        alertNotification.setSequenceNumber(1);
        alertNotification.setSourceAddr("1616");
        alertNotification.setSourceAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        alertNotification.setSourceAddrTon(TypeOfNumber.NATIONAL.value());
        alertNotification.setEsmeAddr("1717");
        alertNotification.setEsmeAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        alertNotification.setEsmeAddrTon(TypeOfNumber.NATIONAL.value());
        SmppMessage smppMessage = binding.createSmppMessage(alertNotification);
        
        assertNull(smppMessage.getBody());
        assertEquals(9, smppMessage.getHeaders().size());
        assertEquals(1, smppMessage.getHeader(SmppBinding.SEQUENCE_NUMBER));
        assertEquals(1, smppMessage.getHeader(SmppBinding.COMMAND_ID));
        assertEquals(0, smppMessage.getHeader(SmppBinding.COMMAND_STATUS));
        assertEquals("1616", smppMessage.getHeader(SmppBinding.SOURCE_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppBinding.SOURCE_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppBinding.SOURCE_ADDR_TON));
        assertEquals("1717", smppMessage.getHeader(SmppBinding.ESME_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppBinding.ESME_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppBinding.ESME_ADDR_TON));
    }

    @Test
    public void createSmppMessageFromDeliveryReceiptShouldReturnASmppMessage() throws Exception {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setSmscDeliveryReceipt();
        deliverSm.setShortMessage("id:2 sub:001 dlvrd:001 submit date:0908312310 done date:0908312311 stat:DELIVRD err:xxx Text:Hello SMPP world!".getBytes());
        SmppMessage smppMessage = binding.createSmppMessage(deliverSm);
        
        assertEquals("Hello SMPP world!", smppMessage.getBody());
        assertEquals(7, smppMessage.getHeaders().size());
        assertEquals("2", smppMessage.getHeader(SmppBinding.ID));
        assertEquals(1, smppMessage.getHeader(SmppBinding.DELIVERED));
        // To avoid the test failure when running in different TimeZone
        //assertEquals(new Date(1251753060000L), smppMessage.getHeader(SmppBinding.DONE_DATE));
        assertEquals("xxx", smppMessage.getHeader(SmppBinding.ERROR));
        //assertEquals(new Date(1251753000000L), smppMessage.getHeader(SmppBinding.SUBMIT_DATE));
        assertEquals(1, smppMessage.getHeader(SmppBinding.SUBMITTED));
        assertEquals(DeliveryReceiptState.DELIVRD, smppMessage.getHeader(SmppBinding.FINAL_STATUS));
    }
    
    @Test
    public void createSmppMessageFromDeliverSmShouldReturnASmppMessage() throws Exception {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setShortMessage("Hello SMPP world!".getBytes());
        deliverSm.setSequenceNumber(1);
        deliverSm.setCommandId(1);
        deliverSm.setSourceAddr("1818");
        deliverSm.setDestAddress("1919");
        deliverSm.setScheduleDeliveryTime("090831230627004+");
        deliverSm.setValidityPeriod("090901230627004+");
        deliverSm.setServiceType("WAP");
        SmppMessage smppMessage = binding.createSmppMessage(deliverSm);
        
        assertEquals("Hello SMPP world!", smppMessage.getBody());
        assertEquals(7, smppMessage.getHeaders().size());
        assertEquals(1, smppMessage.getHeader(SmppBinding.SEQUENCE_NUMBER));
        assertEquals(1, smppMessage.getHeader(SmppBinding.COMMAND_ID));
        assertEquals("1818", smppMessage.getHeader(SmppBinding.SOURCE_ADDR));
        assertEquals("1919", smppMessage.getHeader(SmppBinding.DEST_ADDR));
        assertEquals("090831230627004+", smppMessage.getHeader(SmppBinding.SCHEDULE_DELIVERY_TIME));
        assertEquals("090901230627004+", smppMessage.getHeader(SmppBinding.VALIDITY_PERIOD));
        assertEquals("WAP", smppMessage.getHeader(SmppBinding.SERVICE_TYPE));
    }
    
    @Test
    public void createSmppMessageFromDataSmShouldReturnASmppMessage() throws Exception {
        DataSm dataSm = new DataSm();
        dataSm.setSequenceNumber(1);
        dataSm.setCommandId(1);
        dataSm.setCommandStatus(0);
        dataSm.setSourceAddr("1818");
        dataSm.setSourceAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        dataSm.setSourceAddrTon(TypeOfNumber.NATIONAL.value());
        dataSm.setDestAddress("1919");
        dataSm.setDestAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        dataSm.setDestAddrTon(TypeOfNumber.NATIONAL.value());
        dataSm.setServiceType("WAP");
        dataSm.setRegisteredDelivery((byte) 0);
        SmppMessage smppMessage = binding.createSmppMessage(dataSm, "1");
        
        assertNull(smppMessage.getBody());
        assertEquals(13, smppMessage.getHeaders().size());
        assertEquals("1", smppMessage.getHeader(SmppBinding.ID));
        assertEquals(1, smppMessage.getHeader(SmppBinding.SEQUENCE_NUMBER));
        assertEquals(1, smppMessage.getHeader(SmppBinding.COMMAND_ID));
        assertEquals(0, smppMessage.getHeader(SmppBinding.COMMAND_STATUS));
        assertEquals("1818", smppMessage.getHeader(SmppBinding.SOURCE_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppBinding.SOURCE_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppBinding.SOURCE_ADDR_TON));
        assertEquals("1919", smppMessage.getHeader(SmppBinding.DEST_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppBinding.DEST_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppBinding.DEST_ADDR_TON));
        assertEquals("WAP", smppMessage.getHeader(SmppBinding.SERVICE_TYPE));
        assertEquals((byte) 0, smppMessage.getHeader(SmppBinding.REGISTERED_DELIVERY));
        assertEquals((byte) 0, smppMessage.getHeader(SmppBinding.DATA_CODING));
    }

    @Test
    public void getterShouldReturnTheSetValues() {
        SmppConfiguration configuration = new SmppConfiguration();
        binding.setConfiguration(configuration);
        
        assertSame(configuration, binding.getConfiguration());
    }
}