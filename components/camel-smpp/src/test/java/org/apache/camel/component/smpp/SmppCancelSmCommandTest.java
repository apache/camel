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

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.SMPPSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SmppCancelSmCommandTest {

    private SMPPSession session;
    private SmppConfiguration config;
    private SmppCancelSmCommand command;
    
    @Before
    public void setUp() {
        session = mock(SMPPSession.class);
        config = new SmppConfiguration();
        
        command = new SmppCancelSmCommand(session, config);
    }
    
    @Test
    public void executeWithConfigurationData() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "CancelSm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");
        
        command.execute(exchange);
        
        verify(session).cancelShortMessage("", "1", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, "1616", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, "1717");
        
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
    }
    
    @Test
    public void execute() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "CancelSm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");
        exchange.getIn().setHeader(SmppConstants.SERVICE_TYPE, "XXX");
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_TON, TypeOfNumber.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_NPI, NumberingPlanIndicator.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR, "1818");
        exchange.getIn().setHeader(SmppConstants.DEST_ADDR_TON, TypeOfNumber.INTERNATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.DEST_ADDR_NPI, NumberingPlanIndicator.INTERNET.value());
        exchange.getIn().setHeader(SmppConstants.DEST_ADDR, "1919");
        
        command.execute(exchange);
        
        verify(session).cancelShortMessage("XXX", "1", TypeOfNumber.NATIONAL, NumberingPlanIndicator.NATIONAL, "1818", TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.INTERNET, "1919");
        
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
    }
}
