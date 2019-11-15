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
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;

public class SmppCommandTypeTest {
    
    private Exchange exchange;

    @Before
    public void setUp() {
        exchange = new DefaultExchange(new DefaultCamelContext());
    }

    @Test
    public void createSmppSubmitSmCommand() {
        assertSame(SmppCommandType.SUBMIT_SM, SmppCommandType.fromExchange(exchange));
    }
    
    @Test
    public void createSmppSubmitMultiCommand() {
        exchange.getIn().setHeader(SmppConstants.COMMAND, "SubmitMulti");
        
        assertSame(SmppCommandType.SUBMIT_MULTI, SmppCommandType.fromExchange(exchange));
    }
    
    @Test
    public void createSmppDataSmCommand() {
        exchange.getIn().setHeader(SmppConstants.COMMAND, "DataSm");
        
        assertSame(SmppCommandType.DATA_SHORT_MESSAGE, SmppCommandType.fromExchange(exchange));
    }
    
    @Test
    public void createSmppReplaceSmCommand() {
        exchange.getIn().setHeader(SmppConstants.COMMAND, "ReplaceSm");
        
        assertSame(SmppCommandType.REPLACE_SM, SmppCommandType.fromExchange(exchange));
    }
    
    @Test
    public void createSmppQuerySmCommand() {
        exchange.getIn().setHeader(SmppConstants.COMMAND, "QuerySm");
        
        assertSame(SmppCommandType.QUERY_SM, SmppCommandType.fromExchange(exchange));
    }
    
    @Test
    public void createSmppCancelSmCommand() {
        exchange.getIn().setHeader(SmppConstants.COMMAND, "CancelSm");
        
        assertSame(SmppCommandType.CANCEL_SM, SmppCommandType.fromExchange(exchange));
    }
}
