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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;

public class DescribeTableCommand extends AbstractDdbCommand {

    public DescribeTableCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        DescribeTableResponse result = ddbClient.describeTable(DescribeTableRequest.builder().tableName(determineTableName()).build());

        Message msg = getMessageForResponse(exchange);
        msg.setHeader(Ddb2Constants.TABLE_NAME, result.table().tableName());
        msg.setHeader(Ddb2Constants.TABLE_STATUS, result.table().tableStatus());
        msg.setHeader(Ddb2Constants.CREATION_DATE, result.table().creationDateTime());
        msg.setHeader(Ddb2Constants.ITEM_COUNT, result.table().itemCount());
        msg.setHeader(Ddb2Constants.KEY_SCHEMA, result.table().keySchema());
        msg.setHeader(Ddb2Constants.READ_CAPACITY, result.table().provisionedThroughput().readCapacityUnits());
        msg.setHeader(Ddb2Constants.WRITE_CAPACITY, result.table().provisionedThroughput().writeCapacityUnits());
        msg.setHeader(Ddb2Constants.TABLE_SIZE, result.table().tableSizeBytes());
    }
}
