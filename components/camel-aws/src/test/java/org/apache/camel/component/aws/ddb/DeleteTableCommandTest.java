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
package org.apache.camel.component.aws.ddb;

import java.util.ArrayList;
import java.util.Date;

import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughputDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DeleteTableCommandTest {

    private DeleteTableCommand command;
    private AmazonDDBClientMock ddbClient;
    private DdbConfiguration configuration;
    private Exchange exchange;
    
    @Before
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new DdbConfiguration();
        configuration.setTableName("DOMAIN1");
        exchange = new DefaultExchange(new DefaultCamelContext());
        
        command = new DeleteTableCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void testExecute() {
        command.execute();
        
        assertEquals("DOMAIN1", ddbClient.deleteTableRequest.getTableName());
        assertEquals(new ProvisionedThroughputDescription(), exchange.getIn().getHeader(
                DdbConstants.PROVISIONED_THROUGHPUT));
        assertEquals(new Date(AmazonDDBClientMock.NOW), exchange.getIn().getHeader(DdbConstants.CREATION_DATE,
                Date.class));
        assertEquals(Long.valueOf(10L), exchange.getIn().getHeader(DdbConstants.ITEM_COUNT, Long.class));
        assertEquals(new ArrayList<KeySchemaElement>(), exchange.getIn().getHeader(DdbConstants.KEY_SCHEMA, ArrayList.class));
        assertEquals(Long.valueOf(20L), exchange.getIn().getHeader(DdbConstants.TABLE_SIZE, Long.class));
        assertEquals(TableStatus.ACTIVE, exchange.getIn().getHeader(DdbConstants.TABLE_STATUS, TableStatus.class));
    }
}