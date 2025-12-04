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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Condition;
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
                .keyConditions(determineKeyConditions())
                .exclusiveStartKey(determineExclusiveStartKey())
                .limit(determineLimit())
                .scanIndexForward(determineScanIndexForward());

        // Check if we have set an Index Name
        if (exchange.getIn().getHeader(Ddb2Constants.INDEX_NAME, String.class) != null) {
            query.indexName(exchange.getIn().getHeader(Ddb2Constants.INDEX_NAME, String.class));
        }

        // skip adding attribute-to-get from 'CamelAwsDdbAttributeNames' if the header is null or empty list.
        if (exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTE_NAMES) != null
                && !exchange.getIn()
                        .getHeader(Ddb2Constants.ATTRIBUTE_NAMES, Collection.class)
                        .isEmpty()) {
            query.attributesToGet(determineAttributeNames());
        }

        if (exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION) != null
                && !exchange.getIn()
                        .getHeader(Ddb2Constants.FILTER_EXPRESSION, String.class)
                        .isEmpty()) {
            query.filterExpression(determineFilterExpression());
        }

        if (exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_NAMES) != null
                && !exchange.getIn()
                        .getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_NAMES, Map.class)
                        .isEmpty()) {
            query.expressionAttributeNames(determineFilterExpressionAttributeNames());
        }

        if (exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_VALUES) != null
                && !exchange.getIn()
                        .getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_VALUES, Map.class)
                        .isEmpty()) {
            query.expressionAttributeValues(determineFilterExpressionAttributeValues());
        }

        if (exchange.getIn().getHeader(Ddb2Constants.PROJECT_EXPRESSION) != null
                && !exchange.getIn()
                        .getHeader(Ddb2Constants.PROJECT_EXPRESSION, Map.class)
                        .isEmpty()) {
            query.projectionExpression(determineProjectExpression());
        }

        QueryResponse result = ddbClient.query(query.build());

        Map<Object, Object> tmp = new HashMap<>();
        tmp.put(Ddb2Constants.ITEMS, result.items());
        tmp.put(Ddb2Constants.LAST_EVALUATED_KEY, result.hasLastEvaluatedKey() ? result.lastEvaluatedKey() : null);
        tmp.put(Ddb2Constants.CONSUMED_CAPACITY, result.consumedCapacity());
        tmp.put(Ddb2Constants.COUNT, result.count());
        addToResults(tmp);
    }

    private Boolean determineScanIndexForward() {
        return exchange.getIn().getHeader(Ddb2Constants.SCAN_INDEX_FORWARD, Boolean.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Condition> determineKeyConditions() {
        return exchange.getIn().getHeader(Ddb2Constants.KEY_CONDITIONS, Map.class);
    }

    @SuppressWarnings("unchecked")
    private String determineFilterExpression() {
        return exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION, String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> determineFilterExpressionAttributeNames() {
        return exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_NAMES, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, AttributeValue> determineFilterExpressionAttributeValues() {
        return exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_VALUES, Map.class);
    }

    @SuppressWarnings("unchecked")
    private String determineProjectExpression() {
        return exchange.getIn().getHeader(Ddb2Constants.PROJECT_EXPRESSION, String.class);
    }
}
