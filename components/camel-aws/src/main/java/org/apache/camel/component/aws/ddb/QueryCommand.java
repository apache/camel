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

import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;

import org.apache.camel.Exchange;

public class QueryCommand extends AbstractDdbCommand {
    public QueryCommand(AmazonDynamoDB ddbClient, DdbConfiguration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        QueryResult result = ddbClient.query(new QueryRequest()
                .withTableName(determineTableName())
                .withAttributesToGet(determineAttributeNames())
                .withConsistentRead(determineConsistentRead())
                .withExclusiveStartKey(determineStartKey())
                .withKeyConditions(determineKeyConditions())
                .withExclusiveStartKey(determineStartKey())
                .withLimit(determineLimit())
                .withScanIndexForward(determineScanIndexForward()));

        addToResult(DdbConstants.ITEMS, result.getItems());
        addToResult(DdbConstants.LAST_EVALUATED_KEY, result.getLastEvaluatedKey());
        addToResult(DdbConstants.CONSUMED_CAPACITY, result.getConsumedCapacity());
        addToResult(DdbConstants.COUNT, result.getCount());
    }

    private  Map<String, AttributeValue> determineStartKey() {
        return exchange.getIn().getHeader(DdbConstants.START_KEY, Map.class);
    }

    private Boolean determineScanIndexForward() {
        return exchange.getIn().getHeader(DdbConstants.SCAN_INDEX_FORWARD, Boolean.class);
    }

    private Map determineKeyConditions() {
        return exchange.getIn().getHeader(DdbConstants.KEY_CONDITIONS, Map.class);
    }

    private Integer determineLimit() {
        return exchange.getIn().getHeader(DdbConstants.LIMIT, Integer.class);
    }
}
