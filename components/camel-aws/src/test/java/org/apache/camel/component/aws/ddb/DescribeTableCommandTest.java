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
import java.util.List;

import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DescribeTableCommandTest {

    private DescribeTableCommand command;
    private AmazonDDBClientMock ddbClient;
    private DdbConfiguration configuration;
    private Exchange exchange;

    @Before
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new DdbConfiguration();
        configuration.setTableName("FULL_DESCRIBE_TABLE");
        exchange = new DefaultExchange(new DefaultCamelContext());
        command = new DescribeTableCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void testExecute() {
        command.execute();
        List<KeySchemaElement> keySchema = new ArrayList<KeySchemaElement>();
        keySchema.add(new KeySchemaElement().withAttributeName("name"));
        assertEquals("FULL_DESCRIBE_TABLE", ddbClient.describeTableRequest.getTableName());
        assertEquals("FULL_DESCRIBE_TABLE", exchange.getIn().getHeader(DdbConstants.TABLE_NAME));
        assertEquals("ACTIVE", exchange.getIn().getHeader(DdbConstants.TABLE_STATUS));
        assertEquals(new Date(AmazonDDBClientMock.NOW), exchange.getIn().getHeader(DdbConstants.CREATION_DATE));
        assertEquals(100L, exchange.getIn().getHeader(DdbConstants.ITEM_COUNT));
        assertEquals(keySchema,
                exchange.getIn().getHeader(DdbConstants.KEY_SCHEMA));
        assertEquals(20L, exchange.getIn().getHeader(DdbConstants.READ_CAPACITY));
        assertEquals(10L, exchange.getIn().getHeader(DdbConstants.WRITE_CAPACITY));
        assertEquals(1000L, exchange.getIn().getHeader(DdbConstants.TABLE_SIZE));
    }
}