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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest;

import org.apache.camel.Exchange;

public class UpdateTableCommand extends AbstractDdbCommand {

    public UpdateTableCommand(AmazonDynamoDB ddbClient, DdbConfiguration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        ddbClient.updateTable(new UpdateTableRequest()
                .withTableName(determineTableName())
                .withProvisionedThroughput(new ProvisionedThroughput()
                        .withReadCapacityUnits(determineReadCapacity())
                        .withWriteCapacityUnits(determineWriteCapacity())));
    }

    private Long determineReadCapacity() {
        Long readCapacity = exchange.getIn().getHeader(DdbConstants.READ_CAPACITY, Long.class);
        return readCapacity != null ? readCapacity : configuration.getReadCapacity();
    }


    private Long determineWriteCapacity() {
        Long writeCapacity = exchange.getIn().getHeader(DdbConstants.WRITE_CAPACITY, Long.class);
        return writeCapacity != null ? writeCapacity : configuration.getWriteCapacity();
    }
}
