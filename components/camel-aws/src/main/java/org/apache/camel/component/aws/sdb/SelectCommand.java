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
package org.apache.camel.component.aws.sdb;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static org.apache.camel.component.aws.common.AwsExchangeUtil.getMessageForResponse;

public class SelectCommand extends AbstractSdbCommand {

    public SelectCommand(AmazonSimpleDB sdbClient, SdbConfiguration configuration, Exchange exchange) {
        super(sdbClient, configuration, exchange);
    }

    public void execute() {
        SelectRequest request = new SelectRequest()
            .withSelectExpression(determineSelectExpression())
            .withConsistentRead(determineConsistentRead())
            .withNextToken(determineNextToken());
        log.trace("Sending request [{}] for exchange [{}]...", request, exchange);
        
        SelectResult result = this.sdbClient.select(request);
        
        log.trace("Received result [{}]", result);
        
        Message msg = getMessageForResponse(exchange);
        msg.setHeader(SdbConstants.ITEMS, result.getItems());
        msg.setHeader(SdbConstants.NEXT_TOKEN, result.getNextToken());
    }

    protected String determineSelectExpression() {
        return exchange.getIn().getHeader(SdbConstants.SELECT_EXPRESSION, String.class);
    }
}