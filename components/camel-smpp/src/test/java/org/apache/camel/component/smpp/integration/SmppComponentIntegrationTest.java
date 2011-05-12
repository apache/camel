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
package org.apache.camel.component.smpp.integration;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.smpp.SmppBinding;
import org.apache.camel.component.smpp.SmppMessageType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Spring based integration test for the smpp component. To run this test, ensure that
 * the SMSC is running on:
 * host:     localhost
 * port:     2775
 * user:     smppclient
 * password: password
 * <br/>
 * A SMSC for test is available here: http://www.seleniumsoftware.com/downloads.html
 * 
 * @version 
 */
@Ignore("Must be manually tested")
public class SmppComponentIntegrationTest extends CamelTestSupport {
    
    @Test
    public void sendInOut() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        
        Endpoint start = getMandatoryEndpoint("direct:start");
        Exchange exchange = start.createExchange();
        exchange.setPattern(ExchangePattern.InOut);
        exchange.getIn().setBody("Hello SMPP World!");

        template.send(start, exchange);
        
        assertMockEndpointsSatisfied();
        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals(SmppMessageType.DeliveryReceipt.toString(), resultExchange.getIn().getHeader(SmppBinding.MESSAGE_TYPE));
        assertEquals("Hello SMPP World!", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SmppBinding.ID));
        assertEquals(1, resultExchange.getIn().getHeader(SmppBinding.SUBMITTED));
        assertEquals(1, resultExchange.getIn().getHeader(SmppBinding.DELIVERED));
        assertNotNull(resultExchange.getIn().getHeader(SmppBinding.DONE_DATE));
        assertNotNull(resultExchange.getIn().getHeader(SmppBinding.SUBMIT_DATE));
        assertNull(resultExchange.getIn().getHeader(SmppBinding.ERROR));
        
        assertNotNull(exchange.getOut().getHeader(SmppBinding.ID));
    }
    
    @Test
    public void sendInOnly() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        
        Endpoint start = getMandatoryEndpoint("direct:start");
        Exchange exchange = start.createExchange();
        exchange.setPattern(ExchangePattern.InOnly);
        exchange.getIn().setBody("Hello SMPP World!");

        template.send(start, exchange);
        
        assertMockEndpointsSatisfied();
        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals(SmppMessageType.DeliveryReceipt.toString(), resultExchange.getIn().getHeader(SmppBinding.MESSAGE_TYPE));
        assertEquals("Hello SMPP World!", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SmppBinding.ID));
        assertEquals(1, resultExchange.getIn().getHeader(SmppBinding.SUBMITTED));
        assertEquals(1, resultExchange.getIn().getHeader(SmppBinding.DELIVERED));
        assertNotNull(resultExchange.getIn().getHeader(SmppBinding.DONE_DATE));
        assertNotNull(resultExchange.getIn().getHeader(SmppBinding.SUBMIT_DATE));
        assertNull(resultExchange.getIn().getHeader(SmppBinding.ERROR));
        
        assertNotNull(exchange.getIn().getHeader(SmppBinding.ID));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("smpp://smppclient@localhost:2775?password=password&enquireLinkTimer=3000&transactionTimer=5000&systemType=producer");
                
                from("smpp://smppclient@localhost:2775?password=password&enquireLinkTimer=3000&transactionTimer=5000&systemType=consumer")
                    .to("mock:result");
            }
        };
    }
}