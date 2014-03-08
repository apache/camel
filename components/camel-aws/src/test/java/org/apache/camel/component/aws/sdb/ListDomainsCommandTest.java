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
package org.apache.camel.component.aws.sdb;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ListDomainsCommandTest {

    private ListDomainsCommand command;
    private AmazonSDBClientMock sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;
    
    @Before
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        configuration.setMaxNumberOfDomains(new Integer(5));
        exchange = new DefaultExchange(new DefaultCamelContext());
        
        command = new ListDomainsCommand(sdbClient, configuration, exchange);
    }

    @SuppressWarnings({ "unchecked" })
    @Test
    public void execute() {
        exchange.getIn().setHeader(SdbConstants.NEXT_TOKEN, "TOKEN1");
        
        command.execute();
        
        assertEquals(new Integer(5), sdbClient.listDomainsRequest.getMaxNumberOfDomains());
        assertEquals("TOKEN1", sdbClient.listDomainsRequest.getNextToken());
        
        List<String> domains = exchange.getIn().getHeader(SdbConstants.DOMAIN_NAMES, List.class);
        assertEquals("TOKEN2", exchange.getIn().getHeader(SdbConstants.NEXT_TOKEN));
        assertEquals(2, domains.size());
        assertTrue(domains.contains("DOMAIN1"));
        assertTrue(domains.contains("DOMAIN2"));
    }

    @Test
    public void determineMaxNumberOfDomains() {
        assertEquals(new Integer(5), this.command.determineMaxNumberOfDomains());

        exchange.getIn().setHeader(SdbConstants.MAX_NUMBER_OF_DOMAINS, new Integer(10));

        assertEquals(new Integer(10), this.command.determineMaxNumberOfDomains());
    }
}