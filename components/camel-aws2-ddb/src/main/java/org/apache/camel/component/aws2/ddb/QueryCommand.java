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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

public class QueryCommand extends AbstractDdbCommand {

    public QueryCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        QueryRequest.Builder query = QueryRequest.builder()
                .tableName(determineTableName())
                .attributesToGet(determineAttributeNames())
                .consistentRead(determineConsistentRead())
                .exclusiveStartKey(determineStartKey())
                .keyConditions(determineKeyConditions())
                .exclusiveStartKey(determineStartKey())
                .limit(determineLimit())
                .scanIndexForward(determineScanIndexForward());
        
        // Check if we have set an Index Name
        if (exchange.getIn().getHeader(Ddb2Constants.INDEX_NAME, String.class) != null) {
            query.indexName(exchange.getIn().getHeader(Ddb2Constants.INDEX_NAME, String.class));
        }
        
        QueryResponse result = ddbClient.query(query.build());
        
        Map tmp = new HashMap<>();
        tmp.put(Ddb2Constants.ITEMS, result.items());
        tmp.put(Ddb2Constants.LAST_EVALUATED_KEY, result.lastEvaluatedKey());
        tmp.put(Ddb2Constants.CONSUMED_CAPACITY, result.consumedCapacity());
        tmp.put(Ddb2Constants.COUNT, result.count());
        addToResults(tmp);
    }

    private  Map<String, AttributeValue> determineStartKey() {
        return exchange.getIn().getHeader(Ddb2Constants.START_KEY, Map.class);
    }

    private Boolean determineScanIndexForward() {
        return exchange.getIn().getHeader(Ddb2Constants.SCAN_INDEX_FORWARD, Boolean.class);
    }

    private Map determineKeyConditions() {
        return exchange.getIn().getHeader(Ddb2Constants.KEY_CONDITIONS, Map.class);
    }
}
