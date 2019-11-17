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
package org.apache.camel.component.smpp;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.OctetString;
import org.jsmpp.bean.OptionalParameter.Tag;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.DeliveryReceiptState;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * JUnit test class for <code>org.apache.camel.component.smpp.SmppBinding</code>
 */
public class SmppBindingTest {
    
    private SmppBinding binding;
    private CamelContext camelContext;

    @Before
    public void setUp() {
        binding = new SmppBinding() {
            Date getCurrentDate() {
                return new Date(1251666387000L);
            }
        };
        camelContext = new DefaultCamelContext();
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
        SmppMessage smppMessage = binding.createSmppMessage(camelContext, alertNotification);
        
        assertNull(smppMessage.getBody());
        assertEquals(10, smppMessage.getHeaders().size());
        assertEquals(1, smppMessage.getHeader(SmppConstants.SEQUENCE_NUMBER));
        assertEquals(1, smppMessage.getHeader(SmppConstants.COMMAND_ID));
        assertEquals(0, smppMessage.getHeader(SmppConstants.COMMAND_STATUS));
        assertEquals("1616", smppMessage.getHeader(SmppConstants.SOURCE_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_TON));
        assertEquals("1717", smppMessage.getHeader(SmppConstants.ESME_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppConstants.ESME_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppConstants.ESME_ADDR_TON));
        assertEquals(SmppMessageType.AlertNotification.toString(), smppMessage.getHeader(SmppConstants.MESSAGE_TYPE));
    }

    @Test
    public void createSmppMessageFromDeliveryReceiptShouldReturnASmppMessage() throws Exception {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setSmscDeliveryReceipt();
        deliverSm.setShortMessage(
            "id:2 sub:001 dlvrd:001 submit date:0908312310 done date:0908312311 stat:DELIVRD err:xxx Text:Hello SMPP world!"
                .getBytes());
        SmppMessage smppMessage = binding.createSmppMessage(camelContext, deliverSm);
        
        assertEquals("Hello SMPP world!", smppMessage.getBody());
        assertEquals(8, smppMessage.getHeaders().size());
        assertEquals("2", smppMessage.getHeader(SmppConstants.ID));
        assertEquals(1, smppMessage.getHeader(SmppConstants.DELIVERED));
        // To avoid the test failure when running in different TimeZone
        //assertEquals(new Date(1251753060000L), smppMessage.getHeader(SmppConstants.DONE_DATE));
        assertEquals("xxx", smppMessage.getHeader(SmppConstants.ERROR));
        //assertEquals(new Date(1251753000000L), smppMessage.getHeader(SmppConstants.SUBMIT_DATE));
        assertEquals(1, smppMessage.getHeader(SmppConstants.SUBMITTED));
        assertEquals(DeliveryReceiptState.DELIVRD, smppMessage.getHeader(SmppConstants.FINAL_STATUS));
        assertEquals(SmppMessageType.DeliveryReceipt.toString(), smppMessage.getHeader(SmppConstants.MESSAGE_TYPE));
        assertNull(smppMessage.getHeader(SmppConstants.OPTIONAL_PARAMETERS));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void createSmppMessageFromDeliveryReceiptWithOptionalParametersShouldReturnASmppMessage() throws Exception {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setSmscDeliveryReceipt();
        deliverSm.setShortMessage("id:2 sub:001 dlvrd:001 submit date:0908312310 done date:0908312311 stat:DELIVRD err:xxx Text:Hello SMPP world!".getBytes());
        deliverSm.setOptionalParameters(
            new OptionalParameter.OctetString(Tag.SOURCE_SUBADDRESS, "OctetString"),
            new OptionalParameter.COctetString((short) 0x001D, "COctetString"),
            new OptionalParameter.Byte(Tag.DEST_ADDR_SUBUNIT, (byte) 0x01),
            new OptionalParameter.Short(Tag.DEST_TELEMATICS_ID, (short) 1),
            new OptionalParameter.Int(Tag.QOS_TIME_TO_LIVE, 1),
            new OptionalParameter.Null(Tag.ALERT_ON_MESSAGE_DELIVERY));
        SmppMessage smppMessage = binding.createSmppMessage(camelContext, deliverSm);

        assertEquals("Hello SMPP world!", smppMessage.getBody());
        assertEquals(10, smppMessage.getHeaders().size());
        assertEquals("2", smppMessage.getHeader(SmppConstants.ID));
        assertEquals(1, smppMessage.getHeader(SmppConstants.DELIVERED));
        // To avoid the test failure when running in different TimeZone
        //assertEquals(new Date(1251753060000L), smppMessage.getHeader(SmppConstants.DONE_DATE));
        assertEquals("xxx", smppMessage.getHeader(SmppConstants.ERROR));
        //assertEquals(new Date(1251753000000L), smppMessage.getHeader(SmppConstants.SUBMIT_DATE));
        assertEquals(1, smppMessage.getHeader(SmppConstants.SUBMITTED));
        assertEquals(DeliveryReceiptState.DELIVRD, smppMessage.getHeader(SmppConstants.FINAL_STATUS));
        assertEquals(SmppMessageType.DeliveryReceipt.toString(), smppMessage.getHeader(SmppConstants.MESSAGE_TYPE));

        Map<String, Object> optionalParameters = smppMessage.getHeader(SmppConstants.OPTIONAL_PARAMETERS, Map.class);
        assertEquals(6, optionalParameters.size());
        assertEquals("OctetString", optionalParameters.get("SOURCE_SUBADDRESS"));
        assertEquals("COctetString", optionalParameters.get("ADDITIONAL_STATUS_INFO_TEXT"));
        assertEquals(Byte.valueOf((byte) 0x01), optionalParameters.get("DEST_ADDR_SUBUNIT"));
        assertEquals(Short.valueOf((short) 1), optionalParameters.get("DEST_TELEMATICS_ID"));
        assertEquals(Integer.valueOf(1), optionalParameters.get("QOS_TIME_TO_LIVE"));
        assertNull("0x00", optionalParameters.get("ALERT_ON_MESSAGE_DELIVERY"));

        Map<Short, Object> optionalParameter = smppMessage.getHeader(SmppConstants.OPTIONAL_PARAMETER, Map.class);
        assertEquals(6, optionalParameter.size());
        assertArrayEquals("OctetString".getBytes("UTF-8"), (byte[]) optionalParameter.get(Short.valueOf((short) 0x0202)));
        assertEquals("COctetString", optionalParameter.get(Short.valueOf((short) 0x001D)));
        assertEquals(Byte.valueOf((byte) 0x01), optionalParameter.get(Short.valueOf((short) 0x0005)));
        assertEquals(Short.valueOf((short) 1), optionalParameter.get(Short.valueOf((short) 0x0008)));
        assertEquals(Integer.valueOf(1), optionalParameter.get(Short.valueOf((short) 0x0017)));
        assertNull("0x00", optionalParameter.get(Short.valueOf((short) 0x130C)));
    }

    @Test
    public void createSmppMessageFromDeliveryReceiptWithPayloadInOptionalParameterShouldReturnASmppMessage() {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setSmscDeliveryReceipt();
        deliverSm.setOptionalParameters(new OctetString(OptionalParameter.Tag.MESSAGE_PAYLOAD,
            "id:2 sub:001 dlvrd:001 submit date:0908312310 done date:0908312311 stat:DELIVRD err:xxx Text:Hello SMPP world!"));
        try {
            SmppMessage smppMessage = binding.createSmppMessage(camelContext, deliverSm);

            assertEquals("Hello SMPP world!", smppMessage.getBody());
            assertEquals(10, smppMessage.getHeaders().size());
            assertEquals("2", smppMessage.getHeader(SmppConstants.ID));
            assertEquals(1, smppMessage.getHeader(SmppConstants.DELIVERED));
            assertEquals("xxx", smppMessage.getHeader(SmppConstants.ERROR));
            assertEquals(1, smppMessage.getHeader(SmppConstants.SUBMITTED));
            assertEquals(DeliveryReceiptState.DELIVRD, smppMessage.getHeader(SmppConstants.FINAL_STATUS));
            assertEquals(SmppMessageType.DeliveryReceipt.toString(), smppMessage.getHeader(SmppConstants.MESSAGE_TYPE));
        } catch (Exception e) {
            fail("Should not throw exception while creating smppMessage.");
        }
    }

    @Test
    public void createSmppMessageFromDeliveryReceiptWithoutShortMessageShouldNotThrowException() throws Exception {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setSmscDeliveryReceipt();
        deliverSm.setOptionalParameters(new OptionalParameter.Short((short) 0x2153, (short) 0));

        try {
            SmppMessage smppMessage = binding.createSmppMessage(camelContext, deliverSm);
            Map<Short, Object> optionalParameter = smppMessage.getHeader(SmppConstants.OPTIONAL_PARAMETER, Map.class);
            assertEquals(Short.valueOf((short) 0), optionalParameter.get(Short.valueOf((short) 0x2153)));
        } catch (Exception e) {
            fail("Should not throw exception while creating smppMessage in absence of shortMessage");
        }
    }

    @Test
    public void createSmppMessageFromDeliverSmShouldReturnASmppMessage() throws Exception {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setShortMessage("Hello SMPP world!".getBytes());
        deliverSm.setSequenceNumber(1);
        deliverSm.setCommandId(1);
        deliverSm.setSourceAddr("1818");
        deliverSm.setSourceAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        deliverSm.setSourceAddrTon(TypeOfNumber.NATIONAL.value());
        deliverSm.setDestAddress("1919");
        deliverSm.setDestAddrNpi(NumberingPlanIndicator.INTERNET.value());
        deliverSm.setDestAddrTon(TypeOfNumber.NETWORK_SPECIFIC.value());
        deliverSm.setScheduleDeliveryTime("090831230627004+");
        deliverSm.setValidityPeriod("090901230627004+");
        deliverSm.setServiceType("WAP");
        SmppMessage smppMessage = binding.createSmppMessage(camelContext, deliverSm);
        
        assertEquals("Hello SMPP world!", smppMessage.getBody());
        assertEquals(13, smppMessage.getHeaders().size());
        assertEquals(1, smppMessage.getHeader(SmppConstants.SEQUENCE_NUMBER));
        assertEquals(1, smppMessage.getHeader(SmppConstants.COMMAND_ID));
        assertEquals("1818", smppMessage.getHeader(SmppConstants.SOURCE_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_TON));
        assertEquals("1919", smppMessage.getHeader(SmppConstants.DEST_ADDR));
        assertEquals((byte) 14, smppMessage.getHeader(SmppConstants.DEST_ADDR_NPI));
        assertEquals((byte) 3, smppMessage.getHeader(SmppConstants.DEST_ADDR_TON));
        assertEquals("090831230627004+", smppMessage.getHeader(SmppConstants.SCHEDULE_DELIVERY_TIME));
        assertEquals("090901230627004+", smppMessage.getHeader(SmppConstants.VALIDITY_PERIOD));
        assertEquals("WAP", smppMessage.getHeader(SmppConstants.SERVICE_TYPE));
        assertEquals(SmppMessageType.DeliverSm.toString(), smppMessage.getHeader(SmppConstants.MESSAGE_TYPE));
    }
    
    @Test
    public void createSmppMessageFromDeliverSmWithPayloadInOptionalParameterShouldReturnASmppMessage() throws Exception {
        DeliverSm deliverSm = new DeliverSm();
        deliverSm.setSequenceNumber(1);
        deliverSm.setCommandId(1);
        deliverSm.setSourceAddr("1818");
        deliverSm.setSourceAddrNpi(NumberingPlanIndicator.NATIONAL.value());
        deliverSm.setSourceAddrTon(TypeOfNumber.NATIONAL.value());
        deliverSm.setDestAddress("1919");
        deliverSm.setDestAddrNpi(NumberingPlanIndicator.INTERNET.value());
        deliverSm.setDestAddrTon(TypeOfNumber.NETWORK_SPECIFIC.value());
        deliverSm.setScheduleDeliveryTime("090831230627004+");
        deliverSm.setValidityPeriod("090901230627004+");
        deliverSm.setServiceType("WAP");
        deliverSm.setOptionalParameters(new OctetString(OptionalParameter.Tag.MESSAGE_PAYLOAD, "Hello SMPP world!"));
        SmppMessage smppMessage = binding.createSmppMessage(camelContext, deliverSm);
        
        assertEquals("Hello SMPP world!", smppMessage.getBody());
        assertEquals(13, smppMessage.getHeaders().size());
        assertEquals(1, smppMessage.getHeader(SmppConstants.SEQUENCE_NUMBER));
        assertEquals(1, smppMessage.getHeader(SmppConstants.COMMAND_ID));
        assertEquals("1818", smppMessage.getHeader(SmppConstants.SOURCE_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_TON));
        assertEquals("1919", smppMessage.getHeader(SmppConstants.DEST_ADDR));
        assertEquals((byte) 14, smppMessage.getHeader(SmppConstants.DEST_ADDR_NPI));
        assertEquals((byte) 3, smppMessage.getHeader(SmppConstants.DEST_ADDR_TON));
        assertEquals("090831230627004+", smppMessage.getHeader(SmppConstants.SCHEDULE_DELIVERY_TIME));
        assertEquals("090901230627004+", smppMessage.getHeader(SmppConstants.VALIDITY_PERIOD));
        assertEquals("WAP", smppMessage.getHeader(SmppConstants.SERVICE_TYPE));
        assertEquals(SmppMessageType.DeliverSm.toString(), smppMessage.getHeader(SmppConstants.MESSAGE_TYPE));
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
        SmppMessage smppMessage = binding.createSmppMessage(camelContext, dataSm, "1");
        
        assertNull(smppMessage.getBody());
        assertEquals(14, smppMessage.getHeaders().size());
        assertEquals("1", smppMessage.getHeader(SmppConstants.ID));
        assertEquals(1, smppMessage.getHeader(SmppConstants.SEQUENCE_NUMBER));
        assertEquals(1, smppMessage.getHeader(SmppConstants.COMMAND_ID));
        assertEquals(0, smppMessage.getHeader(SmppConstants.COMMAND_STATUS));
        assertEquals("1818", smppMessage.getHeader(SmppConstants.SOURCE_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppConstants.SOURCE_ADDR_TON));
        assertEquals("1919", smppMessage.getHeader(SmppConstants.DEST_ADDR));
        assertEquals((byte) 8, smppMessage.getHeader(SmppConstants.DEST_ADDR_NPI));
        assertEquals((byte) 2, smppMessage.getHeader(SmppConstants.DEST_ADDR_TON));
        assertEquals("WAP", smppMessage.getHeader(SmppConstants.SERVICE_TYPE));
        assertEquals((byte) 0, smppMessage.getHeader(SmppConstants.REGISTERED_DELIVERY));
        assertEquals((byte) 0, smppMessage.getHeader(SmppConstants.DATA_CODING));
        assertEquals(SmppMessageType.DataSm.toString(), smppMessage.getHeader(SmppConstants.MESSAGE_TYPE));
    }

    @Test
    public void createSmppMessageFrom8bitDataCodingDeliverSmShouldNotModifyBody() throws Exception {
        final Set<String> encodings = Charset.availableCharsets().keySet();

        final byte[] dataCodings = {
            (byte)0x02,
            (byte)0x04,
            (byte)0xF6,
            (byte)0xF4
        };

        byte[] body = {
            (byte)0xFF, 'A', 'B', (byte)0x00,
            (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF
        };

        DeliverSm deliverSm = new DeliverSm();

        for (byte dataCoding : dataCodings) {
            deliverSm.setDataCoding(dataCoding);
            deliverSm.setShortMessage(body);

            for (String encoding : encodings) {
                binding.getConfiguration().setEncoding(encoding);
                SmppMessage smppMessage = binding.createSmppMessage(camelContext, deliverSm);
                assertArrayEquals(
                    String.format("data coding=0x%02X; encoding=%s",
                                  dataCoding,
                                  encoding),
                    body,
                    smppMessage.getBody(byte[].class));
            }
        }
    }

    @Test
    public void getterShouldReturnTheSetValues() {
        SmppConfiguration configuration = new SmppConfiguration();
        binding.setConfiguration(configuration);
        
        assertSame(configuration, binding.getConfiguration());
    }
    
    @Test
    public void createSmppSubmitSmCommand() {
        SMPPSession session = new SMPPSession();
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        
        SmppCommand command = binding.createSmppCommand(session, exchange);
        
        assertTrue(command instanceof SmppSubmitSmCommand);
    }
    
    @Test
    public void createSmppSubmitMultiCommand() {
        SMPPSession session = new SMPPSession();
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(SmppConstants.COMMAND, "SubmitMulti");
        
        SmppCommand command = binding.createSmppCommand(session, exchange);
        
        assertTrue(command instanceof SmppSubmitMultiCommand);
    }
    
    @Test
    public void createSmppDataSmCommand() {
        SMPPSession session = new SMPPSession();
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(SmppConstants.COMMAND, "DataSm");
        
        SmppCommand command = binding.createSmppCommand(session, exchange);
        
        assertTrue(command instanceof SmppDataSmCommand);
    }
    
    @Test
    public void createSmppReplaceSmCommand() {
        SMPPSession session = new SMPPSession();
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        
        SmppCommand command = binding.createSmppCommand(session, exchange);
        
        assertTrue(command instanceof SmppReplaceSmCommand);
    }
    
    @Test
    public void createSmppQuerySmCommand() {
        SMPPSession session = new SMPPSession();
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(SmppConstants.COMMAND, "QuerySm");
        
        SmppCommand command = binding.createSmppCommand(session, exchange);
        
        assertTrue(command instanceof SmppQuerySmCommand);
    }
    
    @Test
    public void createSmppCancelSmCommand() {
        SMPPSession session = new SMPPSession();
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(SmppConstants.COMMAND, "CancelSm");
        
        SmppCommand command = binding.createSmppCommand(session, exchange);
        
        assertTrue(command instanceof SmppCancelSmCommand);
    }
}
