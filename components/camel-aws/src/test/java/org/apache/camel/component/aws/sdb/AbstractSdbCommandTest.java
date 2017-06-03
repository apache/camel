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

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.UpdateCondition;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class AbstractSdbCommandTest {

    private AbstractSdbCommand command;
    private AmazonSimpleDB sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;
    
    @Before
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        configuration.setConsistentRead(Boolean.TRUE);
        exchange = new DefaultExchange(new DefaultCamelContext());
        
        this.command = new AbstractSdbCommand(sdbClient, configuration, exchange) {
            @Override
            public void execute() {
                // noop
            }
        };
    }
    
    @Test
    public void determineDomainName() {
        assertEquals("DOMAIN1", this.command.determineDomainName());
        
        exchange.getIn().setHeader(SdbConstants.DOMAIN_NAME, "DOMAIN2");
        
        assertEquals("DOMAIN2", this.command.determineDomainName());
    }
    
    @Test
    public void determineItemName() {
        try {
            this.command.determineItemName();
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("AWS SDB Item Name header is missing.", e.getMessage());
        }
        
        exchange.getIn().setHeader(SdbConstants.ITEM_NAME, "ITEM1");
        
        assertEquals("ITEM1", this.command.determineItemName());
    }
    
    @Test
    public void determineConsistentRead() {
        assertEquals(Boolean.TRUE, this.command.determineConsistentRead());
        
        exchange.getIn().setHeader(SdbConstants.CONSISTENT_READ, Boolean.FALSE);
        
        assertEquals(Boolean.FALSE, this.command.determineConsistentRead());
    }
    
    @Test
    public void determineUpdateCondition() {
        assertNull(this.command.determineUpdateCondition());
        
        UpdateCondition condition = new UpdateCondition("Key1", "Value1", true);
        exchange.getIn().setHeader(SdbConstants.UPDATE_CONDITION, condition);
        
        assertSame(condition, this.command.determineUpdateCondition());
    }
    
    @Test
    public void determineNextToken() {
        assertNull(this.command.determineNextToken());
        
        exchange.getIn().setHeader(SdbConstants.NEXT_TOKEN, "Token1");
        
        assertEquals("Token1", this.command.determineNextToken());
    }
}