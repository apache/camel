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

import java.util.Date;
import java.util.TimeZone;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.SMPPSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SmppReplaceSmCommandTest {
    
    private static TimeZone defaultTimeZone;

    private SMPPSession session;
    private SmppConfiguration config;
    private SmppReplaceSmCommand command;
    
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
        
        command = new SmppReplaceSmCommand(session, config);
    }
    
    @Test
    public void executeWithConfigurationData() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");
        exchange.getIn().setBody("new short message body");
        
        command.execute(exchange);
        
        verify(session).replaceShortMessage(eq("1"), eq(TypeOfNumber.UNKNOWN), eq(NumberingPlanIndicator.UNKNOWN), eq("1616"), (String) isNull(), (String) isNull(),
                eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)), eq((byte) 0), eq("new short message body".getBytes()));
        
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
    }
    
    @Test
    public void execute() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_TON, TypeOfNumber.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_NPI, NumberingPlanIndicator.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR, "1818");
        exchange.getIn().setHeader(SmppConstants.SCHEDULE_DELIVERY_TIME, new Date(1111111));
        exchange.getIn().setHeader(SmppConstants.VALIDITY_PERIOD, new Date(2222222));
        exchange.getIn().setHeader(SmppConstants.REGISTERED_DELIVERY, new RegisteredDelivery(SMSCDeliveryReceipt.FAILURE).value());
        exchange.getIn().setBody("new short message body");
        
        command.execute(exchange);
        
        verify(session).replaceShortMessage(eq("1"), eq(TypeOfNumber.NATIONAL), eq(NumberingPlanIndicator.NATIONAL), eq("1818"), eq("-300101001831100+"), eq("-300101003702200+"),
                eq(new RegisteredDelivery(SMSCDeliveryReceipt.FAILURE)), eq((byte) 0), eq("new short message body".getBytes()));
        
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
    }
    
    @Test
    public void executeWithValidityPeriodAsString() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_TON, TypeOfNumber.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_NPI, NumberingPlanIndicator.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR, "1818");
        exchange.getIn().setHeader(SmppConstants.SCHEDULE_DELIVERY_TIME, new Date(1111111));
        exchange.getIn().setHeader(SmppConstants.VALIDITY_PERIOD, "000003000000000R"); // three days
        exchange.getIn().setHeader(SmppConstants.REGISTERED_DELIVERY, new RegisteredDelivery(SMSCDeliveryReceipt.FAILURE).value());
        exchange.getIn().setBody("new short message body");
        
        command.execute(exchange);
        
        verify(session).replaceShortMessage(eq("1"), eq(TypeOfNumber.NATIONAL), eq(NumberingPlanIndicator.NATIONAL), eq("1818"), eq("-300101001831100+"), eq("000003000000000R"),
                eq(new RegisteredDelivery(SMSCDeliveryReceipt.FAILURE)), eq((byte) 0), eq("new short message body".getBytes()));
        
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
    }

    @Test
    public void bodyWithSmscDefaultDataCodingNarrowedToCharset() throws Exception {
        final int dataCoding = 0x00; /* SMSC-default */
        byte[] body = {(byte)0xFF, 'A', 'B', (byte)0x00, (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF};
        byte[] bodyNarrowed = {'?', 'A', 'B', '\0', '?', (byte)0x7F, 'C', '?'};

        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.DATA_CODING, dataCoding);
        exchange.getIn().setBody(body);

        command.execute(exchange);
        
        verify(session).replaceShortMessage((String) isNull(),
                                            eq(TypeOfNumber.UNKNOWN),
                                            eq(NumberingPlanIndicator.UNKNOWN),
                                            eq("1616"),
                                            (String) isNull(),
                                            (String) isNull(),
                                            eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                                            eq((byte) 0),
                                            eq(bodyNarrowed));
    }

    @Test
    public void bodyWithLatin1DataCodingNarrowedToCharset() throws Exception {
        final int dataCoding = 0x03; /* ISO-8859-1 (Latin1) */
        byte[] body = {(byte)0xFF, 'A', 'B', (byte)0x00, (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF};
        byte[] bodyNarrowed = {'?', 'A', 'B', '\0', '?', (byte)0x7F, 'C', '?'};

        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.DATA_CODING, dataCoding);
        exchange.getIn().setBody(body);

        command.execute(exchange);
        
        verify(session).replaceShortMessage((String) isNull(),
                                            eq(TypeOfNumber.UNKNOWN),
                                            eq(NumberingPlanIndicator.UNKNOWN),
                                            eq("1616"),
                                            (String) isNull(),
                                            (String) isNull(),
                                            eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                                            eq((byte) 0),
                                            eq(bodyNarrowed));
    }

    @Test
    public void bodyWithSMPP8bitDataCodingNotModified() throws Exception {
        final int dataCoding = 0x04; /* SMPP 8-bit */
        byte[] body = {(byte)0xFF, 'A', 'B', (byte)0x00, (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF};

        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.DATA_CODING, dataCoding);
        exchange.getIn().setBody(body);

        command.execute(exchange);
        
        verify(session).replaceShortMessage((String) isNull(),
                                            eq(TypeOfNumber.UNKNOWN),
                                            eq(NumberingPlanIndicator.UNKNOWN),
                                            eq("1616"),
                                            (String) isNull(),
                                            (String) isNull(),
                                            eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                                            eq((byte) 0),
                                            eq(body));

    }

    @Test
    public void bodyWithGSM8bitDataCodingNotModified() throws Exception {
        final int dataCoding = 0xF7; /* GSM 8-bit class 3 */
        byte[] body = {(byte)0xFF, 'A', 'B', (byte)0x00, (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF};

        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.DATA_CODING, dataCoding);
        exchange.getIn().setBody(body);

        command.execute(exchange);
        
        verify(session).replaceShortMessage((String) isNull(),
                                            eq(TypeOfNumber.UNKNOWN),
                                            eq(NumberingPlanIndicator.UNKNOWN),
                                            eq("1616"),
                                            (String) isNull(),
                                            (String) isNull(),
                                            eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                                            eq((byte) 0),
                                            eq(body));
    }

    @Test
    public void eightBitDataCodingOverridesDefaultAlphabet() throws Exception {
        final int binDataCoding = 0xF7; /* GSM 8-bit class 3 */
        byte[] body = {(byte)0xFF, 'A', 'B', (byte)0x00, (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF};

        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.ALPHABET, Alphabet.ALPHA_DEFAULT.value());
        exchange.getIn().setHeader(SmppConstants.DATA_CODING, binDataCoding);
        exchange.getIn().setBody(body);

        command.execute(exchange);
        
        verify(session).replaceShortMessage((String) isNull(),
                                            eq(TypeOfNumber.UNKNOWN),
                                            eq(NumberingPlanIndicator.UNKNOWN),
                                            eq("1616"),
                                            (String) isNull(),
                                            (String) isNull(),
                                            eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                                            eq((byte) 0),
                                            eq(body));
    }

    @Test
    public void latin1DataCodingOverridesEightBitAlphabet() throws Exception {
        final int latin1DataCoding = 0x03; /* ISO-8859-1 (Latin1) */
        byte[] body = {(byte)0xFF, 'A', 'B', (byte)0x00, (byte)0xFF, (byte)0x7F, 'C', (byte)0xFF};
        byte[] bodyNarrowed = {'?', 'A', 'B', '\0', '?', (byte)0x7F, 'C', '?'};

        Exchange exchange = new DefaultExchange(new DefaultCamelContext(),
                                                ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        exchange.getIn().setHeader(SmppConstants.ALPHABET, Alphabet.ALPHA_8_BIT.value());
        exchange.getIn().setHeader(SmppConstants.DATA_CODING, latin1DataCoding);
        exchange.getIn().setBody(body);

        command.execute(exchange);
        
        verify(session).replaceShortMessage((String) isNull(),
                                            eq(TypeOfNumber.UNKNOWN),
                                            eq(NumberingPlanIndicator.UNKNOWN),
                                            eq("1616"),
                                            (String) isNull(),
                                            (String) isNull(),
                                            eq(new RegisteredDelivery(SMSCDeliveryReceipt.SUCCESS_FAILURE)),
                                            eq((byte) 0),
                                            eq(bodyNarrowed));
    }
}
