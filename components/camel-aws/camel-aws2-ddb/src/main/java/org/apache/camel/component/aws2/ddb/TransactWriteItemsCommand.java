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
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

public class TransactWriteItemsCommand extends AbstractDdbCommand {

    public TransactWriteItemsCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        TransactWriteItemsRequest.Builder builder = TransactWriteItemsRequest.builder()
                .transactItems(determineTransactWriteItems());

        String clientRequestToken = exchange.getIn().getHeader(Ddb2Constants.TRANSACT_CLIENT_REQUEST_TOKEN, String.class);
        if (ObjectHelper.isNotEmpty(clientRequestToken)) {
            builder.clientRequestToken(clientRequestToken);
        }

        TransactWriteItemsResponse result = ddbClient.transactWriteItems(builder.build());

        Map<Object, Object> tmp = new HashMap<>();
        if (result.consumedCapacity() != null) {
            tmp.put(Ddb2Constants.TRANSACT_WRITE_CONSUMED_CAPACITY, result.consumedCapacity());
        }
        if (result.itemCollectionMetrics() != null) {
            tmp.put(Ddb2Constants.TRANSACT_WRITE_ITEM_COLLECTION_METRICS, result.itemCollectionMetrics());
        }
        addToResults(tmp);
    }

    @SuppressWarnings("unchecked")
    private List<TransactWriteItem> determineTransactWriteItems() {
        return exchange.getIn().getHeader(Ddb2Constants.TRANSACT_WRITE_ITEMS, List.class);
    }
}
