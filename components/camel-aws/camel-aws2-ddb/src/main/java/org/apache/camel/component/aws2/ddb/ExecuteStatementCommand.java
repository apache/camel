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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

public class ExecuteStatementCommand extends AbstractDdbCommand {

    public ExecuteStatementCommand(DynamoDbClient ddbClient, Ddb2Configuration configuration, Exchange exchange) {
        super(ddbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        ExecuteStatementRequest.Builder builder = ExecuteStatementRequest.builder()
                .statement(determineStatement())
                .consistentRead(determineConsistentRead());

        List<AttributeValue> parameters = determineStatementParameters();
        if (ObjectHelper.isNotEmpty(parameters)) {
            builder.parameters(parameters);
        }

        String nextToken = exchange.getIn().getHeader(Ddb2Constants.NEXT_TOKEN, String.class);
        if (ObjectHelper.isNotEmpty(nextToken)) {
            builder.nextToken(nextToken);
        }

        ExecuteStatementResponse result = ddbClient.executeStatement(builder.build());

        Map<Object, Object> tmp = new HashMap<>();
        tmp.put(Ddb2Constants.EXECUTE_STATEMENT_ITEMS, result.items());
        tmp.put(Ddb2Constants.NEXT_TOKEN, result.nextToken());
        addToResults(tmp);
    }

    private String determineStatement() {
        return exchange.getIn().getHeader(Ddb2Constants.STATEMENT, String.class);
    }

    @SuppressWarnings("unchecked")
    private List<AttributeValue> determineStatementParameters() {
        return exchange.getIn().getHeader(Ddb2Constants.STATEMENT_PARAMETERS, List.class);
    }
}
