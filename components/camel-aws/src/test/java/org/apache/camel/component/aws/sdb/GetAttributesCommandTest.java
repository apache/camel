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

import com.amazonaws.services.simpledb.model.Attribute;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GetAttributesCommandTest {

    private GetAttributesCommand command;
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
        
        command = new GetAttributesCommand(sdbClient, configuration, exchange);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void execute() {
        List<String> attributeNames = new ArrayList<String>();
        attributeNames.add("ATTRIBUTE1");
        exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_NAMES, attributeNames);
        exchange.getIn().setHeader(SdbConstants.ITEM_NAME, "ITEM1");
        
        command.execute();
        
        assertEquals("DOMAIN1", sdbClient.getAttributesRequest.getDomainName());
        assertEquals("ITEM1", sdbClient.getAttributesRequest.getItemName());
        assertEquals(Boolean.TRUE, sdbClient.getAttributesRequest.getConsistentRead());
        assertEquals(attributeNames, sdbClient.getAttributesRequest.getAttributeNames());
        
        List<Attribute> attributes = exchange.getIn().getHeader(SdbConstants.ATTRIBUTES, List.class);
        assertEquals(2, attributes.size());
        assertEquals("AttributeOne", attributes.get(0).getName());
        assertEquals("Value One", attributes.get(0).getValue());
        assertEquals("AttributeTwo", attributes.get(1).getName());
        assertEquals("Value Two", attributes.get(1).getValue());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void executeWithoutItemName() {
        List<String> attributeNames = new ArrayList<String>();
        attributeNames.add("ATTRIBUTE1");
        exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_NAMES, attributeNames);
        
        command.execute();
    }
    
    @Test
    public void determineAttributeNames() {
        assertNull(this.command.determineAttributeNames());
        
        List<String> attributeNames = new ArrayList<String>();
        attributeNames.add("ATTRIBUTE1");
        exchange.getIn().setHeader(SdbConstants.ATTRIBUTE_NAMES, attributeNames);
        
        assertEquals(attributeNames, this.command.determineAttributeNames());
    }
}