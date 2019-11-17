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
import com.amazonaws.services.simpledb.model.UpdateCondition;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSdbCommand {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    protected AmazonSimpleDB sdbClient;
    protected SdbConfiguration configuration;
    protected Exchange exchange;

    public AbstractSdbCommand(AmazonSimpleDB sdbClient, SdbConfiguration configuration, Exchange exchange) {
        this.sdbClient = sdbClient;
        this.configuration = configuration;
        this.exchange = exchange;
    }

    public abstract void execute();

    protected String determineDomainName() {
        String domainName = exchange.getIn().getHeader(SdbConstants.DOMAIN_NAME, String.class);
        return domainName != null ? domainName : configuration.getDomainName();
    }
    
    protected String determineItemName() {
        String key = exchange.getIn().getHeader(SdbConstants.ITEM_NAME, String.class);
        if (key == null) {
            throw new IllegalArgumentException("AWS SDB Item Name header is missing.");
        }
        return key;
    }
    
    protected Boolean determineConsistentRead() {
        return exchange.getIn().getHeader(SdbConstants.CONSISTENT_READ, this.configuration.isConsistentRead(), Boolean.class);
    }
    
    protected UpdateCondition determineUpdateCondition() {
        return exchange.getIn().getHeader(SdbConstants.UPDATE_CONDITION, UpdateCondition.class);
    }
    
    protected String determineNextToken() {
        return exchange.getIn().getHeader(SdbConstants.NEXT_TOKEN, String.class);
    }
}
