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

import com.amazonaws.services.simpledb.model.ReplaceableItem;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BatchPutAttributesCommandTest {

    private BatchPutAttributesCommand command;
    private AmazonSDBClientMock sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;
    
    @Before
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        
        command = new BatchPutAttributesCommand(sdbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        List<ReplaceableItem> replaceableItems = new ArrayList<ReplaceableItem>();
        replaceableItems.add(new ReplaceableItem("ITEM1"));
        exchange.getIn().setHeader(SdbConstants.REPLACEABLE_ITEMS, replaceableItems);
        
        command.execute();
        
        assertEquals("DOMAIN1", sdbClient.batchPutAttributesRequest.getDomainName());
        assertEquals(replaceableItems, sdbClient.batchPutAttributesRequest.getItems());
    }

    @Test
    public void determineReplaceableItems() {
        assertNull(this.command.determineReplaceableItems());
        
        List<ReplaceableItem> replaceableItems = new ArrayList<ReplaceableItem>();
        replaceableItems.add(new ReplaceableItem("ITEM1"));
        exchange.getIn().setHeader(SdbConstants.REPLACEABLE_ITEMS, replaceableItems);
        
        assertEquals(replaceableItems, this.command.determineReplaceableItems());
    }
}