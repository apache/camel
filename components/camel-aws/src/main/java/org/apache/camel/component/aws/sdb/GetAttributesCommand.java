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

import java.util.Collection;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.GetAttributesRequest;
import com.amazonaws.services.simpledb.model.GetAttributesResult;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

import static org.apache.camel.component.aws.common.AwsExchangeUtil.getMessageForResponse;

public class GetAttributesCommand extends AbstractSdbCommand {
    
    public GetAttributesCommand(AmazonSimpleDB sdbClient, SdbConfiguration configuration, Exchange exchange) {
        super(sdbClient, configuration, exchange);
    }

    public void execute() {
        GetAttributesRequest request = new GetAttributesRequest()
            .withDomainName(determineDomainName())
            .withItemName(determineItemName())
            .withConsistentRead(determineConsistentRead())
            .withAttributeNames(determineAttributeNames());
        log.trace("Sending request [{}] for exchange [{}]...", request, exchange);
        
        GetAttributesResult result = this.sdbClient.getAttributes(request);
        
        log.trace("Received result [{}]", result);
        
        Message msg = getMessageForResponse(exchange);
        msg.setHeader(SdbConstants.ATTRIBUTES, result.getAttributes());
    }

    @SuppressWarnings("unchecked")
    protected Collection<String> determineAttributeNames() {
        return exchange.getIn().getHeader(SdbConstants.ATTRIBUTE_NAMES, Collection.class);
    }
}