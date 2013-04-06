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

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.simpledb.model.Item;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BatchDeleteAttributesCommandTest {
    
    private BatchDeleteAttributesCommand command;
    private AmazonSDBClientMock sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;

    @Before
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        
        command = new BatchDeleteAttributesCommand(sdbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        List<Item> deletableItems = new ArrayList<Item>();
        deletableItems.add(new Item());
        exchange.getIn().setHeader(SdbConstants.DELETABLE_ITEMS, deletableItems);
        
        command.execute();
        
        assertEquals("DOMAIN1", sdbClient.batchDeleteAttributesRequest.getDomainName());
        assertEquals(deletableItems, sdbClient.batchDeleteAttributesRequest.getItems());
    }

    @Test
    public void determineDeletableItems() {
        assertNull(this.command.determineDeletableItems());
        
        List<Item> deletableItems = new ArrayList<Item>();
        deletableItems.add(new Item());
        exchange.getIn().setHeader(SdbConstants.DELETABLE_ITEMS, deletableItems);
        
        assertEquals(deletableItems, this.command.determineDeletableItems());
    }
}
