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
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItem;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactGetItemsResponse;

public class TransactGetItemsCommand extends AbstractDdbCommand {

    public TransactGetItemsCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        TransactGetItemsResponse result = ddbClient.transactGetItems(
                TransactGetItemsRequest.builder()
                        .transactItems(determineTransactGetItems())
                        .build());

        Map<Object, Object> tmp = new HashMap<>();
        tmp.put(Ddb2Constants.TRANSACT_GET_RESPONSE, result.responses());
        addToResults(tmp);
    }

    @SuppressWarnings("unchecked")
    private List<TransactGetItem> determineTransactGetItems() {
        return exchange.getIn().getHeader(Ddb2Constants.TRANSACT_GET_ITEMS, List.class);
    }
}
