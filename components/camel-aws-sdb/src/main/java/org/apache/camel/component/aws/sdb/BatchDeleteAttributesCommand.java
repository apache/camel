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

import java.util.Collection;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.BatchDeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.DeletableItem;
import org.apache.camel.Exchange;

public class BatchDeleteAttributesCommand extends AbstractSdbCommand {

    public BatchDeleteAttributesCommand(AmazonSimpleDB sdbClient, SdbConfiguration configuration, Exchange exchange) {
        super(sdbClient, configuration, exchange);
    }

    @Override
    public void execute() {
        BatchDeleteAttributesRequest request = new BatchDeleteAttributesRequest()
            .withDomainName(determineDomainName())
            .withItems(determineDeletableItems());
        log.trace("Sending request [{}] for exchange [{}]...", request, exchange);
        
        this.sdbClient.batchDeleteAttributes(request);
        
        log.trace("Request sent");
    }

    @SuppressWarnings("unchecked")
    protected Collection<DeletableItem> determineDeletableItems() {
        return exchange.getIn().getHeader(SdbConstants.DELETABLE_ITEMS, Collection.class);
    }
}
