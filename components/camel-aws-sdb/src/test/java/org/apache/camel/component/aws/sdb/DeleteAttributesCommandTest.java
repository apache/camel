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
package org.apache.camel.component.aws.sdb;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.UpdateCondition;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DeleteAttributesCommandTest {

    private DeleteAttributesCommand command;
    private AmazonSDBClientMock sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;
    
    @Before
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        
        command = new DeleteAttributesCommand(sdbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("NAME1", "VALUE1"));
        exchange.getIn().setHeader(SdbConstants.ATTRIBUTES, attributes);
        exchange.getIn().setHeader(SdbConstants.ITEM_NAME, "ITEM1");
        UpdateCondition condition = new UpdateCondition("Key1", "Value1", true);
        exchange.getIn().setHeader(SdbConstants.UPDATE_CONDITION, condition);
        
        command.execute();
        
        assertEquals("DOMAIN1", sdbClient.deleteAttributesRequest.getDomainName());
        assertEquals("ITEM1", sdbClient.deleteAttributesRequest.getItemName());
        assertEquals(condition, sdbClient.deleteAttributesRequest.getExpected());
        assertEquals(attributes, sdbClient.deleteAttributesRequest.getAttributes());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void executeWithoutItemName() {
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("NAME1", "VALUE1"));
        exchange.getIn().setHeader(SdbConstants.ATTRIBUTES, attributes);
        UpdateCondition condition = new UpdateCondition("Key1", "Value1", true);
        exchange.getIn().setHeader(SdbConstants.UPDATE_CONDITION, condition);
        
        command.execute();
    }

    @Test
    public void determineAttributes() {
        assertNull(this.command.determineAttributes());
        
        List<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("NAME1", "VALUE1"));
        exchange.getIn().setHeader(SdbConstants.ATTRIBUTES, attributes);
        
        assertEquals(attributes, this.command.determineAttributes());
    }
}
