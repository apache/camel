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

import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.UpdateCondition;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PutAttributesCommandTest {

    private PutAttributesCommand command;
    private AmazonSDBClientMock sdbClient;
    private SdbConfiguration configuration;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        sdbClient = new AmazonSDBClientMock();
        configuration = new SdbConfiguration();
        configuration.setDomainName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());

        command = new PutAttributesCommand(sdbClient, configuration, exchange);
    }

    @Test
    public void execute() {
        List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
        replaceableAttributes.add(new ReplaceableAttribute("NAME1", "VALUE1", true));
        exchange.getIn().setHeader(SdbConstants.REPLACEABLE_ATTRIBUTES, replaceableAttributes);
        exchange.getIn().setHeader(SdbConstants.ITEM_NAME, "ITEM1");
        UpdateCondition updateCondition = new UpdateCondition("NAME1", "VALUE1", true);
        exchange.getIn().setHeader(SdbConstants.UPDATE_CONDITION, updateCondition);

        command.execute();

        assertEquals("DOMAIN1", sdbClient.putAttributesRequest.getDomainName());
        assertEquals("ITEM1", sdbClient.putAttributesRequest.getItemName());
        assertEquals(updateCondition, sdbClient.putAttributesRequest.getExpected());
        assertEquals(replaceableAttributes, sdbClient.putAttributesRequest.getAttributes());
    }

    @Test
    public void executeWithoutItemName() {
        List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
        replaceableAttributes.add(new ReplaceableAttribute("NAME1", "VALUE1", true));
        exchange.getIn().setHeader(SdbConstants.REPLACEABLE_ATTRIBUTES, replaceableAttributes);
        UpdateCondition updateCondition = new UpdateCondition("NAME1", "VALUE1", true);
        exchange.getIn().setHeader(SdbConstants.UPDATE_CONDITION, updateCondition);

        assertThrows(IllegalArgumentException.class,
                () -> command.execute());
    }

    @Test
    public void determineReplaceableAttributes() {
        assertNull(this.command.determineReplaceableAttributes());

        List<ReplaceableAttribute> replaceableAttributes = new ArrayList<>();
        replaceableAttributes.add(new ReplaceableAttribute("NAME1", "VALUE1", true));
        exchange.getIn().setHeader(SdbConstants.REPLACEABLE_ATTRIBUTES, replaceableAttributes);

        assertEquals(replaceableAttributes, this.command.determineReplaceableAttributes());
    }
}
