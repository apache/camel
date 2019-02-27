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

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;

import org.apache.camel.Exchange;

public class ScanCommand extends AbstractDdbCommand {

    public ScanCommand(AmazonDynamoDB ddbClient, DdbConfiguration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        ScanResult result = ddbClient.scan(new ScanRequest()
                .withTableName(determineTableName())
                .withScanFilter(determineScanFilter()));

        Map tmp = new HashMap<>();
        tmp.put(DdbConstants.ITEMS, result.getItems());
        tmp.put(DdbConstants.LAST_EVALUATED_KEY, result.getLastEvaluatedKey());
        tmp.put(DdbConstants.CONSUMED_CAPACITY, result.getConsumedCapacity());
        tmp.put(DdbConstants.COUNT, result.getCount());
        tmp.put(DdbConstants.SCANNED_COUNT, result.getScannedCount());
        addToResults(tmp);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Condition> determineScanFilter() {
        return exchange.getIn().getHeader(DdbConstants.SCAN_FILTER, Map.class);
    }
}
