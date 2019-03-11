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
import com.amazonaws.services.dynamodbv2.model.BatchGetItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchGetItemResult;
import com.amazonaws.services.dynamodbv2.model.KeysAndAttributes;

import org.apache.camel.Exchange;

public class BatchGetItemsCommand extends AbstractDdbCommand {

    public BatchGetItemsCommand(AmazonDynamoDB ddbClient, DdbConfiguration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        BatchGetItemResult result = ddbClient.batchGetItem(
                new BatchGetItemRequest().withRequestItems(determineBatchItems()));

        Map tmp = new HashMap<>();
        tmp.put(DdbConstants.BATCH_RESPONSE, result.getResponses());
        tmp.put(DdbConstants.UNPROCESSED_KEYS, result.getUnprocessedKeys());
        addToResults(tmp);        
    }

    @SuppressWarnings("unchecked")
    private Map<String, KeysAndAttributes> determineBatchItems() {
        return exchange.getIn().getHeader(DdbConstants.BATCH_ITEMS, Map.class);
    }
}

