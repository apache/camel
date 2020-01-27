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
import org.jsmpp.bean.MessageState;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SmppQuerySmCommandTest {

    private SMPPSession session;
    private SmppConfiguration config;
    private SmppQuerySmCommand command;
    
    @Before
    public void setUp() {
        session = mock(SMPPSession.class);
        config = new SmppConfiguration();
        
        command = new SmppQuerySmCommand(session, config);
    }
    
    @Test
    public void executeWithConfigurationData() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "QuerySm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");
        when(session.queryShortMessage("1", TypeOfNumber.UNKNOWN, NumberingPlanIndicator.UNKNOWN, "1616"))
                .thenReturn(new QuerySmResult("-300101010000004+", MessageState.DELIVERED, (byte) 0));
        
        command.execute(exchange);
        
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
        assertEquals("DELIVERED", exchange.getMessage().getHeader(SmppConstants.MESSAGE_STATE));
        assertEquals((byte) 0, exchange.getMessage().getHeader(SmppConstants.ERROR));
        assertNotNull(exchange.getMessage().getHeader(SmppConstants.FINAL_DATE));
    }
    
    @Test
    public void execute() throws Exception {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);
        exchange.getIn().setHeader(SmppConstants.COMMAND, "QuerySm");
        exchange.getIn().setHeader(SmppConstants.ID, "1");
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_TON, TypeOfNumber.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR_NPI, NumberingPlanIndicator.NATIONAL.value());
        exchange.getIn().setHeader(SmppConstants.SOURCE_ADDR, "1818");
        when(session.queryShortMessage("1", TypeOfNumber.NATIONAL, NumberingPlanIndicator.NATIONAL, "1818"))
                .thenReturn(new QuerySmResult("-300101010000004+", MessageState.DELIVERED, (byte) 0));
        
        command.execute(exchange);
        
        assertEquals("1", exchange.getMessage().getHeader(SmppConstants.ID));
        assertEquals("DELIVERED", exchange.getMessage().getHeader(SmppConstants.MESSAGE_STATE));
        assertEquals((byte) 0, exchange.getMessage().getHeader(SmppConstants.ERROR));
        assertNotNull(exchange.getMessage().getHeader(SmppConstants.FINAL_DATE));
    }
}
