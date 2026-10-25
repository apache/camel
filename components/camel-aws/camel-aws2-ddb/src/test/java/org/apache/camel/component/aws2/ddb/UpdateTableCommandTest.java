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
package org.apache.camel.component.aws2.ddb;

import java.time.Instant;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UpdateTableCommandTest {

    private UpdateTableCommand command;
    private AmazonDDBClientMock ddbClient;
    private Ddb2Configuration configuration;
    private Exchange exchange;

    @BeforeEach
    public void setUp() {
        ddbClient = new AmazonDDBClientMock();
        configuration = new Ddb2Configuration();
        configuration.setTableName("DOMAIN1");
        configuration.setReadCapacity(20L);
        configuration.setWriteCapacity(30L);
        exchange = new DefaultExchange(new DefaultCamelContext());

        command = new UpdateTableCommand(ddbClient, configuration, exchange);
    }

    @Test
    public void testExecute() {
        command.execute();

        assertEquals("DOMAIN1", ddbClient.updateTableRequest.tableName());
        assertEquals(Long.valueOf(20), ddbClient.updateTableRequest.provisionedThroughput().readCapacityUnits());
        assertEquals(Long.valueOf(30), ddbClient.updateTableRequest.provisionedThroughput().writeCapacityUnits());

        assertEquals(TableStatus.UPDATING, exchange.getIn().getHeader(Ddb2Constants.TABLE_STATUS));
        assertEquals(Instant.ofEpochMilli(AmazonDDBClientMock.NOW), exchange.getIn().getHeader(Ddb2Constants.CREATION_DATE));
        assertEquals(100L, exchange.getIn().getHeader(Ddb2Constants.ITEM_COUNT));
        assertEquals(1000L, exchange.getIn().getHeader(Ddb2Constants.TABLE_SIZE));
        assertEquals(List.of(KeySchemaElement.builder().attributeName("name").build()),
                exchange.getIn().getHeader(Ddb2Constants.KEY_SCHEMA));
        assertEquals(Long.valueOf(5), exchange.getIn().getHeader(Ddb2Constants.PROVISIONED_THROUGHPUT,
                ProvisionedThroughputDescription.class).readCapacityUnits());
        // the table name and the capacity headers are inputs: they are not set from the response
        assertNull(exchange.getIn().getHeader(Ddb2Constants.TABLE_NAME));
        assertNull(exchange.getIn().getHeader(Ddb2Constants.READ_CAPACITY));
        assertNull(exchange.getIn().getHeader(Ddb2Constants.WRITE_CAPACITY));
    }
}
