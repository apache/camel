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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.UpdateTableRequest;

public class UpdateTableCommand extends AbstractDdbCommand {

    public UpdateTableCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        ddbClient.updateTable(UpdateTableRequest.builder().tableName(determineTableName())
            .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(determineReadCapacity()).writeCapacityUnits(determineWriteCapacity()).build()).build());
    }

    private Long determineReadCapacity() {
        Long readCapacity = exchange.getIn().getHeader(Ddb2Constants.READ_CAPACITY, Long.class);
        return readCapacity != null ? readCapacity : configuration.getReadCapacity();
    }

    private Long determineWriteCapacity() {
        Long writeCapacity = exchange.getIn().getHeader(Ddb2Constants.WRITE_CAPACITY, Long.class);
        return writeCapacity != null ? writeCapacity : configuration.getWriteCapacity();
    }
}
