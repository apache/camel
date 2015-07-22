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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedRecipientListMBean;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.URISupport;

/**
 * @version 
 */
@ManagedResource(description = "Managed RecipientList")
public class ManagedRecipientList extends ManagedProcessor implements ManagedRecipientListMBean {
    private final RecipientList processor;
    private String uri;

    public ManagedRecipientList(CamelContext context, RecipientList processor, RecipientListDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        boolean sanitize = strategy.getManagementAgent().getMask() != null ? strategy.getManagementAgent().getMask() : false;
        uri = getDefinition().getExpression().getExpression();
        if (sanitize) {
            uri = URISupport.sanitizeUri(uri);
        }
    }

    @Override
    public RecipientListDefinition getDefinition() {
        return (RecipientListDefinition) super.getDefinition();
    }

    @Override
    public String getExpressionLanguage() {
        return getDefinition().getExpression().getLanguage();
    }

    @Override
    public String getExpression() {
        return uri;
    }

    @Override
    public String getUriDelimiter() {
        return processor.getDelimiter();
    }

    @Override
    public Integer getCacheSize() {
        return processor.getCacheSize();
    }

    @Override
    public Boolean isParallelAggregate() {
        return processor.isParallelAggregate();
    }

    @Override
    public Boolean isParallelProcessing() {
        return processor.isParallelProcessing();
    }

    @Override
    public Boolean isStreaming() {
        return processor.isStreaming();
    }

    @Override
    public Boolean isStopOnException() {
        return processor.isStopOnException();
    }

    @Override
    public Boolean isShareUnitOfWork() {
        return processor.isShareUnitOfWork();
    }

    @Override
    public Long getTimeout() {
        return processor.getTimeout();
    }
}
