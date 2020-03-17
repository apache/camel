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
import java.util.Map;

import org.apache.camel.Exchange;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.Condition;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

public class ScanCommand extends AbstractDdbCommand {

    public ScanCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        ScanResponse result = ddbClient.scan(ScanRequest.builder().tableName(determineTableName()).limit(determineLimit()).exclusiveStartKey(determineExclusiveStartKey())
            .scanFilter(determineScanFilter()).build());

        Map<Object, Object> tmp = new HashMap<>();
        tmp.put(Ddb2Constants.ITEMS, result.items());
        tmp.put(Ddb2Constants.LAST_EVALUATED_KEY, result.lastEvaluatedKey());
        tmp.put(Ddb2Constants.CONSUMED_CAPACITY, result.consumedCapacity());
        tmp.put(Ddb2Constants.COUNT, result.count());
        tmp.put(Ddb2Constants.SCANNED_COUNT, result.scannedCount());
        addToResults(tmp);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Condition> determineScanFilter() {
        return exchange.getIn().getHeader(Ddb2Constants.SCAN_FILTER, Map.class);
    }
}
