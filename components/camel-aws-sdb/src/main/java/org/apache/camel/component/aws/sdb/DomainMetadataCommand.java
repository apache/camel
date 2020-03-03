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
package org.apache.camel.component.aws.sdb;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.DomainMetadataRequest;
import com.amazonaws.services.simpledb.model.DomainMetadataResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;

public class DomainMetadataCommand extends AbstractSdbCommand {

    public DomainMetadataCommand(AmazonSimpleDB sdbClient, SdbConfiguration configuration, Exchange exchange) {
        super(sdbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        DomainMetadataRequest request = new DomainMetadataRequest()
            .withDomainName(determineDomainName());
        log.trace("Sending request [{}] for exchange [{}]...", request, exchange);
        
        DomainMetadataResult result = this.sdbClient.domainMetadata(request);
        
        log.trace("Received result [{}]", result);
        
        Message msg = getMessageForResponse(exchange);
        msg.setHeader(SdbConstants.TIMESTAMP, result.getTimestamp());
        msg.setHeader(SdbConstants.ITEM_COUNT, result.getItemCount());
        msg.setHeader(SdbConstants.ATTRIBUTE_NAME_COUNT, result.getAttributeNameCount());
        msg.setHeader(SdbConstants.ATTRIBUTE_VALUE_COUNT, result.getAttributeValueCount());
        msg.setHeader(SdbConstants.ATTRIBUTE_NAME_SIZE, result.getAttributeNamesSizeBytes());
        msg.setHeader(SdbConstants.ATTRIBUTE_VALUE_SIZE, result.getAttributeValuesSizeBytes());
        msg.setHeader(SdbConstants.ITEM_NAME_SIZE, result.getItemNamesSizeBytes());
    }
    
    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
