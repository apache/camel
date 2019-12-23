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
package org.apache.camel.component.aws.ddb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

public class DescribeTableCommand extends AbstractDdbCommand {

    public DescribeTableCommand(AmazonDynamoDB ddbClient, DdbConfiguration configuration,
                                Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        DescribeTableResult result = ddbClient.describeTable(new DescribeTableRequest()
                .withTableName(determineTableName()));

        Message msg = getMessageForResponse(exchange);
        msg.setHeader(DdbConstants.TABLE_NAME, result.getTable().getTableName());
        msg.setHeader(DdbConstants.TABLE_STATUS, result.getTable().getTableStatus());
        msg.setHeader(DdbConstants.CREATION_DATE, result.getTable().getCreationDateTime());
        msg.setHeader(DdbConstants.ITEM_COUNT, result.getTable().getItemCount());
        msg.setHeader(DdbConstants.KEY_SCHEMA, result.getTable().getKeySchema());
        msg.setHeader(DdbConstants.READ_CAPACITY,
                result.getTable().getProvisionedThroughput().getReadCapacityUnits());
        msg.setHeader(DdbConstants.WRITE_CAPACITY,
                result.getTable().getProvisionedThroughput().getWriteCapacityUnits());
        msg.setHeader(DdbConstants.TABLE_SIZE, result.getTable().getTableSizeBytes());
    }
}
