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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.Tag;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.MessageId;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SmppDataSmCommandTest {

    private static TimeZone defaultTimeZone;

    private SMPPSession session;
    private SmppConfiguration config;
    private SmppDataSmCommand command;

    @BeforeClass
    public static void setUpBeforeClass() {
        defaultTimeZone = TimeZone.getDefault();
        
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
    }

    @AfterClass
    public static void tearDownAfterClass() {
        if (defaultTimeZone != null) {
            TimeZone.setDefault(defaultTimeZone);
        }
    }

    @Before
    public void setUp() {
        session = mock(SMPPSession.class);
        config = new SmppConfiguration();
        config.setServiceType("CMT");

        command = new SmppDataSmCommand(session, config);
    }

    @Test
    public void executeWithConfigurationData() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "DataSm");
        when(session.dataShortMessage(eq("CMT"), eq(TypeOfNumber.UNKNOWN), eq(NumberingPlanIndicator.UNKNOWN), eq("1616"),
                eq(TypeOfNumber.UNKNOWN), eq(NumberingPlanIndicator.UNKNOWN), eq("1717"), eq(new ESMClass()),
                eq(new RegisteredDelivery((byte) 1)), eq(DataCodings.newInstance((byte) 0))))
            .thenReturn(new DataSmResult(new MessageId("1"), null));

        command.execute(exchange);

        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
        assertNull(exchange.getMessage().getHeader(SmppConstants.OPTIONAL_PARAMETERS));
    }

    @Test
    public void execute() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "DataSm");
        exchange.getIn().setHeader(SmppConstants.SERVICE_TYPE, "XXX");
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_TON, TypeOfNumber.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_NPI, NumberingPlanIndicator.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR, "1818");
        exchange.getIn().setHeader(SmppConstants.DEST_ADDR_TON, TypeOfNumber.INTERNATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.DEST_ADDR_NPI, NumberingPlanIndicator.INTERNET.value());
        exchange.getIn().setHeader(SmppConstants.DEST_ADDR, "1919");
        exchange.getIn().setHeader(SmppConstants.REGISTERED_DELIVERY, new RegisteredDelivery(SMSCDeliveryReceipt.FAILURE).value());
        when(session.dataShortMessage(eq("XXX"), eq(TypeOfNumber.NATIONAL), eq(NumberingPlanIndicator.NATIONAL), eq("1818"),
                eq(TypeOfNumber.INTERNATIONAL), eq(NumberingPlanIndicator.INTERNET), eq("1919"), eq(new ESMClass()),
                eq(new RegisteredDelivery((byte) 2)), eq(DataCodings.newInstance((byte) 0))))
            .thenReturn(new DataSmResult(new MessageId("1"), null));

        command.execute(exchange);

        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
        assertNull(exchange.getMessage().getHeader(SmppConstants.OPTIONAL_PARAMETERS));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void executeWithOptionalParameter() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "DataSm");
        Map<String, String> optionalParameters = new LinkedHashMap<>();
        optionalParameters.put("SOURCE_SUBADDRESS", "1292");
        optionalParameters.put("ADDITIONAL_STATUS_INFO_TEXT", "urgent");
        optionalParameters.put("DEST_ADDR_SUBUNIT", "4");
        optionalParameters.put("DEST_TELEMATICS_ID", "2");
        optionalParameters.put("QOS_TIME_TO_LIVE", "3600000");
        optionalParameters.put("ALERT_ON_MESSAGE_DELIVERY", null);
        // fall back test for vendor specific optional parameter 
        optionalParameters.put("0x2150", "0815");
        optionalParameters.put("0x2151", "0816");
        optionalParameters.put("0x2152", "6");
        optionalParameters.put("0x2153", "9");
        optionalParameters.put("0x2154", "7400000");
        optionalParameters.put("0x2155", null);
        exchange.getIn().setHeader(SmppConstants.OPTIONAL_PARAMETERS, optionalParameters);
        when(session.dataShortMessage(eq("CMT"), eq(TypeOfNumber.UNKNOWN), eq(NumberingPlanIndicator.UNKNOWN), eq("1616"),
                eq(TypeOfNumber.UNKNOWN), eq(NumberingPlanIndicator.UNKNOWN), eq("1717"), eq(new ESMClass()),
                eq(new RegisteredDelivery((byte) 1)), eq(DataCodings.newInstance((byte) 0)),
                eq(new OptionalParameter.Source_subaddress("1292".getBytes())),
                eq(new OptionalParameter.Additional_status_info_text("urgent")),
                eq(new OptionalParameter.Dest_addr_subunit((byte) 4)),
                eq(new OptionalParameter.Dest_telematics_id((short) 2)),
                eq(new OptionalParameter.Qos_time_to_live(3600000)),
                eq(new OptionalParameter.Alert_on_message_delivery((byte) 0))))
            .thenReturn(new DataSmResult(new MessageId("1"), new OptionalParameter[] {new OptionalParameter.Source_subaddress("1292".getBytes()),
                new OptionalParameter.Additional_status_info_text("urgent"),
                new OptionalParameter.Dest_addr_subunit((byte) 4),
                new OptionalParameter.Dest_telematics_id((short) 2),
                new OptionalParameter.Qos_time_to_live(3600000),
                new OptionalParameter.Alert_on_message_delivery((byte) 0)}));

        command.execute(exchange);

        assertEquals(3, exchange.getMessage().getHeaders().size());
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));

        Map<String, String> optParamMap = exchange.getMessage().getHeader(SmppConstants.OPTIONAL_PARAMETERS, Map.class);
        assertEquals(6, optParamMap.size());
        assertEquals("1292", optParamMap.get("SOURCE_SUBADDRESS"));
        assertEquals("urgent", optParamMap.get("ADDITIONAL_STATUS_INFO_TEXT"));
        assertEquals("4", optParamMap.get("DEST_ADDR_SUBUNIT"));
        assertEquals("2", optParamMap.get("DEST_TELEMATICS_ID"));
        assertEquals("3600000", optParamMap.get("QOS_TIME_TO_LIVE"));
        assertEquals("0", optParamMap.get("ALERT_ON_MESSAGE_DELIVERY"));

        Map<Short, Object> optionalResultParameter = exchange.getMessage().getHeader(SmppConstants.OPTIONAL_PARAMETER, Map.class);
        assertEquals(6, optionalResultParameter.size());
        assertArrayEquals("1292".getBytes("UTF-8"), (byte[]) optionalResultParameter.get((short) 0x0202));
        assertEquals("urgent", optionalResultParameter.get((short) 0x001D));
        assertEquals((byte) 4, optionalResultParameter.get((short) 0x0005));
        assertEquals((short) 2, optionalResultParameter.get((short) 0x0008));
        assertEquals(3600000, optionalResultParameter.get((short) 0x0017));
        assertEquals((byte) 0, optionalResultParameter.get((short) 0x130C));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void executeWithOptionalParameterNewStyle() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "DataSm");
        Map<Short, Object> optionalParameters = new LinkedHashMap<>();
        // standard optional parameter
        optionalParameters.put((short) 0x0202, "1292".getBytes("UTF-8"));
        optionalParameters.put((short) 0x001D, "urgent");
        optionalParameters.put((short) 0x0005, Byte.valueOf("4"));
        optionalParameters.put((short) 0x0008, (short) 2);
        optionalParameters.put((short) 0x0017, 3600000);
        optionalParameters.put((short) 0x130C, null);
        // vendor specific optional parameter
        optionalParameters.put((short) 0x2150, "0815".getBytes("UTF-8"));
        optionalParameters.put((short) 0x2151, "0816");
        optionalParameters.put((short) 0x2152, Byte.valueOf("6"));
        optionalParameters.put((short) 0x2153, (short) 9);
        optionalParameters.put((short) 0x2154, 7400000);
        optionalParameters.put((short) 0x2155, null);
        exchange.getIn().setHeader(SmppConstants.OPTIONAL_PARAMETER, optionalParameters);
        when(session.dataShortMessage(eq("CMT"), eq(TypeOfNumber.UNKNOWN), eq(NumberingPlanIndicator.UNKNOWN), eq("1616"),
                eq(TypeOfNumber.UNKNOWN), eq(NumberingPlanIndicator.UNKNOWN), eq("1717"), eq(new ESMClass()),
                eq(new RegisteredDelivery((byte) 1)), eq(DataCodings.newInstance((byte) 0)),
                eq(new OptionalParameter.OctetString(Tag.SOURCE_SUBADDRESS, "1292")),
                eq(new OptionalParameter.COctetString(Tag.ADDITIONAL_STATUS_INFO_TEXT.code(), "urgent")),
                eq(new OptionalParameter.Byte(Tag.DEST_ADDR_SUBUNIT, (byte) 4)),
                eq(new OptionalParameter.Short(Tag.DEST_TELEMATICS_ID.code(), (short) 2)),
                eq(new OptionalParameter.Int(Tag.QOS_TIME_TO_LIVE, 3600000)),
                eq(new OptionalParameter.Null(Tag.ALERT_ON_MESSAGE_DELIVERY)),
                eq(new OptionalParameter.OctetString((short) 0x2150, "1292", "UTF-8")),
                eq(new OptionalParameter.COctetString((short) 0x2151, "0816")),
                eq(new OptionalParameter.Byte((short) 0x2152, (byte) 6)),
                eq(new OptionalParameter.Short((short) 0x2153, (short) 9)),
                eq(new OptionalParameter.Int((short) 0x2154, 7400000)),
                eq(new OptionalParameter.Null((short) 0x2155))))
            .thenReturn(new DataSmResult(new MessageId("1"), new OptionalParameter[]{
                new OptionalParameter.Source_subaddress("1292".getBytes()), new OptionalParameter.Additional_status_info_text("urgent"),
                new OptionalParameter.Dest_addr_subunit((byte) 4), new OptionalParameter.Dest_telematics_id((short) 2),
                new OptionalParameter.Qos_time_to_live(3600000), new OptionalParameter.Alert_on_message_delivery((byte) 0)}));

        command.execute(exchange);

        assertEquals(3, exchange.getMessage().getHeaders().size());
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));

        Map<String, String> optParamMap = exchange.getMessage().getHeader(SmppConstants.OPTIONAL_PARAMETERS, Map.class);
        assertEquals(6, optParamMap.size());
        assertEquals("1292", optParamMap.get("SOURCE_SUBADDRESS"));
        assertEquals("urgent", optParamMap.get("ADDITIONAL_STATUS_INFO_TEXT"));
        assertEquals("4", optParamMap.get("DEST_ADDR_SUBUNIT"));
        assertEquals("2", optParamMap.get("DEST_TELEMATICS_ID"));
        assertEquals("3600000", optParamMap.get("QOS_TIME_TO_LIVE"));
        assertEquals("0", optParamMap.get("ALERT_ON_MESSAGE_DELIVERY"));

        Map<Short, Object> optionalResultParameter = exchange.getMessage().getHeader(SmppConstants.OPTIONAL_PARAMETER, Map.class);
        assertEquals(6, optionalResultParameter.size());
        assertArrayEquals("1292".getBytes("UTF-8"), (byte[]) optionalResultParameter.get((short) 0x0202));
        assertEquals("urgent", optionalResultParameter.get((short) 0x001D));
        assertEquals((byte) 4, optionalResultParameter.get((short) 0x0005));
        assertEquals((short) 2, optionalResultParameter.get((short) 0x0008));
        assertEquals(3600000, optionalResultParameter.get((short) 0x0017));
        assertEquals((byte) 0, optionalResultParameter.get((short) 0x130C));
    }
}
