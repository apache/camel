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

import com.amazonaws.services.simpledb.model.Item;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SelectCommandTest {

    private SelectCommand command;
    private AmazonSDBClientMock sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;
    
    @Before
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        configuration.setConsistentRead(Boolean.TRUE);
        exchange = new DefaultExchange(new DefaultCamelContext());
        
        command = new SelectCommand(sdbClient, configuration, exchange);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void execute() {
        exchange.getIn().setHeader(SdbConstants.NEXT_TOKEN, "TOKEN1");
        exchange.getIn().setHeader(SdbConstants.SELECT_EXPRESSION, "SELECT NAME1 FROM DOMAIN1 WHERE NAME1 LIKE 'VALUE1'");
        
        command.execute();
        
        assertEquals(Boolean.TRUE, sdbClient.selectRequest.getConsistentRead());
        assertEquals("TOKEN1", sdbClient.selectRequest.getNextToken());
        assertEquals("SELECT NAME1 FROM DOMAIN1 WHERE NAME1 LIKE 'VALUE1'", sdbClient.selectRequest.getSelectExpression());
        
        List<Item> items = exchange.getIn().getHeader(SdbConstants.ITEMS, List.class);
        assertEquals("TOKEN2", exchange.getIn().getHeader(SdbConstants.NEXT_TOKEN));
        assertEquals(2, items.size());
        assertEquals("ITEM1", items.get(0).getName());
        assertEquals("ITEM2", items.get(1).getName());
    }
    
    @Test
    public void determineSelectExpression() {
        assertNull(this.command.determineSelectExpression());

        exchange.getIn().setHeader(SdbConstants.SELECT_EXPRESSION, "SELECT NAME1 FROM DOMAIN1 WHERE NAME1 LIKE 'VALUE1'");

        assertEquals("SELECT NAME1 FROM DOMAIN1 WHERE NAME1 LIKE 'VALUE1'", this.command.determineSelectExpression());
    }
}