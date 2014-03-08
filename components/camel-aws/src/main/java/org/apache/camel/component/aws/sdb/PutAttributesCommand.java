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
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;

import org.apache.camel.Exchange;

public class PutAttributesCommand extends AbstractSdbCommand {
    
    public PutAttributesCommand(AmazonSimpleDB sdbClient, SdbConfiguration configuration, Exchange exchange) {
        super(sdbClient, configuration, exchange);
    }

    public void execute() {
        PutAttributesRequest request = new PutAttributesRequest()
            .withDomainName(determineDomainName())
            .withItemName(determineItemName())
            .withAttributes(determineReplaceableAttributes())
            .withExpected(determineUpdateCondition());
        log.trace("Sending request [{}] for exchange [{}]...", request, exchange);

        this.sdbClient.putAttributes(request);
        
        log.trace("Request sent");
    }
    
    @SuppressWarnings("unchecked")
    protected Collection<ReplaceableAttribute> determineReplaceableAttributes() {
        return exchange.getIn().getHeader(SdbConstants.REPLACEABLE_ATTRIBUTES, Collection.class);
    }
}