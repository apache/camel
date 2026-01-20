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
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ExpectedAttributeValue;

public abstract class AbstractDdbCommand {
    protected Ddb2Configuration configuration;
    protected Exchange exchange;
    protected DynamoDbClient ddbClient;

    protected AbstractDdbCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {

        this.ddbClient = ddbClient;
        this.configuration = configuration;
        this.exchange = exchange;
    }

    public abstract void execute();

    protected Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }

    protected String determineTableName() {
        String tableName = exchange.getIn().getHeader(Ddb2Constants.TABLE_NAME, String.class);
        return tableName != null ? tableName : configuration.getTableName();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, ExpectedAttributeValue> determineUpdateCondition() {
        return exchange.getIn().getHeader(Ddb2Constants.UPDATE_CONDITION, Map.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, AttributeValue> determineItem() {
        return exchange.getIn().getHeader(Ddb2Constants.ITEM, Map.class);
    }

    protected String determineReturnValues() {
        return exchange.getIn().getHeader(Ddb2Constants.RETURN_VALUES, String.class);
    }

    protected void addAttributesToResult(Map<String, AttributeValue> attributes) {
        Message msg = getMessageForResponse(exchange);
        msg.setHeader(Ddb2Constants.ATTRIBUTES, attributes);
    }

    protected void addToResults(Map<Object, Object> map) {
        Message msg = getMessageForResponse(exchange);
        for (Map.Entry<Object, Object> en : map.entrySet()) {
            msg.setHeader((String) en.getKey(), en.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, AttributeValue> determineKey() {
        return exchange.getIn().getHeader(Ddb2Constants.KEY, Map.class);
    }

    @SuppressWarnings("unchecked")
    protected Collection<String> determineAttributeNames() {
        return exchange.getIn().getHeader(Ddb2Constants.ATTRIBUTE_NAMES, Collection.class);
    }

    protected Boolean determineConsistentRead() {
        return exchange.getIn().getHeader(Ddb2Constants.CONSISTENT_READ, configuration.isConsistentRead(), Boolean.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, AttributeValue> determineExclusiveStartKey() {
        return exchange.getIn().getHeader(Ddb2Constants.START_KEY, Map.class);
    }

    protected Integer determineLimit() {
        return exchange.getIn().getHeader(Ddb2Constants.LIMIT, Integer.class);
    }

    /**
     * Gets the filter expression from the exchange header.
     */
    protected String determineFilterExpression() {
        return exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION, String.class);
    }

    /**
     * Gets the filter expression attribute names from the exchange header.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, String> determineFilterExpressionAttributeNames() {
        return exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_NAMES, Map.class);
    }

    /**
     * Gets the filter expression attribute values from the exchange header.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, AttributeValue> determineFilterExpressionAttributeValues() {
        return exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_VALUES, Map.class);
    }

    /**
     * Gets the projection expression from the exchange header.
     */
    protected String determineProjectExpression() {
        return exchange.getIn().getHeader(Ddb2Constants.PROJECT_EXPRESSION, String.class);
    }

    /**
     * Gets the index name from the exchange header.
     */
    protected String determineIndexName() {
        return exchange.getIn().getHeader(Ddb2Constants.INDEX_NAME, String.class);
    }

    /**
     * Checks if the filter expression is set and not empty.
     */
    protected boolean hasFilterExpression() {
        String filterExpression = exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION, String.class);
        return filterExpression != null && !filterExpression.isEmpty();
    }

    /**
     * Checks if the filter expression attribute names are set and not empty.
     */
    protected boolean hasFilterExpressionAttributeNames() {
        Map<?, ?> names = exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_NAMES, Map.class);
        return names != null && !names.isEmpty();
    }

    /**
     * Checks if the filter expression attribute values are set and not empty.
     */
    protected boolean hasFilterExpressionAttributeValues() {
        Map<?, ?> values = exchange.getIn().getHeader(Ddb2Constants.FILTER_EXPRESSION_ATTRIBUTE_VALUES, Map.class);
        return values != null && !values.isEmpty();
    }

    /**
     * Checks if the projection expression is set and not empty.
     */
    protected boolean hasProjectExpression() {
        String projectExpression = exchange.getIn().getHeader(Ddb2Constants.PROJECT_EXPRESSION, String.class);
        return projectExpression != null && !projectExpression.isEmpty();
    }
}
