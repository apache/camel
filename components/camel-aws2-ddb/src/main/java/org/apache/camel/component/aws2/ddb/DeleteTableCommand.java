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

import java.util.HashMap;

import org.apache.camel.Exchange;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

public class DeleteTableCommand extends AbstractDdbCommand {

    public DeleteTableCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        TableDescription tableDescription = ddbClient.deleteTable(DeleteTableRequest.builder().tableName(determineTableName()).build()).tableDescription();

        HashMap<Object, Object> tmp = new HashMap<>();
        tmp.put(Ddb2Constants.PROVISIONED_THROUGHPUT, tableDescription.provisionedThroughput());
        tmp.put(Ddb2Constants.CREATION_DATE, tableDescription.creationDateTime());
        tmp.put(Ddb2Constants.ITEM_COUNT, tableDescription.itemCount());
        tmp.put(Ddb2Constants.KEY_SCHEMA, tableDescription.keySchema());
        tmp.put(Ddb2Constants.TABLE_NAME, tableDescription.tableName());
        tmp.put(Ddb2Constants.TABLE_SIZE, tableDescription.tableSizeBytes());
        tmp.put(Ddb2Constants.TABLE_STATUS, tableDescription.tableStatus());
        addToResults(tmp);
    }
}
