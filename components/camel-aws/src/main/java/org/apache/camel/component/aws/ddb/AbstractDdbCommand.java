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

import java.util.Collection;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.aws.common.AwsExchangeUtil;

public abstract class AbstractDdbCommand {
    protected DdbConfiguration configuration;
    protected Exchange exchange;
    protected AmazonDynamoDB ddbClient;

    public AbstractDdbCommand(AmazonDynamoDB ddbClient,
                              DdbConfiguration configuration, Exchange exchange) {

        this.ddbClient = ddbClient;
        this.configuration = configuration;
        this.exchange = exchange;
    }

    public abstract void execute();

    protected Message getMessageForResponse(Exchange exchange) {
        return AwsExchangeUtil.getMessageForResponse(exchange);
    }

    protected String determineTableName() {
        String tableName = exchange.getIn().getHeader(DdbConstants.TABLE_NAME, String.class);
        return tableName != null ? tableName : configuration.getTableName();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, ExpectedAttributeValue> determineUpdateCondition() {
        return exchange.getIn().getHeader(DdbConstants.UPDATE_CONDITION, Map.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, AttributeValue> determineItem() {
        return exchange.getIn().getHeader(DdbConstants.ITEM, Map.class);
    }

    protected String determineReturnValues() {
        return exchange.getIn().getHeader(DdbConstants.RETURN_VALUES, String.class);
    }

    protected void addAttributesToResult(Map<String, AttributeValue> attributes) {
        Message msg = getMessageForResponse(exchange);
        msg.setHeader(DdbConstants.ATTRIBUTES, attributes);
    }
    
    protected void addToResults(Map<String, Object> map) {
        Message msg = getMessageForResponse(exchange);
        for (Map.Entry<String, Object> en : map.entrySet()) {
            msg.setHeader(en.getKey(), en.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, AttributeValue> determineKey() {
        return exchange.getIn().getHeader(DdbConstants.KEY, Map.class);
    }

    @SuppressWarnings("unchecked")
    protected Collection<String> determineAttributeNames() {
        return exchange.getIn().getHeader(DdbConstants.ATTRIBUTE_NAMES, Collection.class);
    }

    protected Boolean determineConsistentRead() {
        return exchange.getIn().getHeader(DdbConstants.CONSISTENT_READ, configuration.isConsistentRead(), Boolean.class);
    }
}