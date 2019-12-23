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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedResequencerMBean;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Resequencer;
import org.apache.camel.processor.StreamResequencer;

@ManagedResource(description = "Managed Resequencer")
public class ManagedResequencer extends ManagedProcessor implements ManagedResequencerMBean {
    private final Resequencer processor;
    private final StreamResequencer streamProcessor;
    private final String expression;

    public ManagedResequencer(CamelContext context, Resequencer processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = processor;
        this.streamProcessor = null;
        this.expression = processor.getExpression().toString();
    }

    public ManagedResequencer(CamelContext context, StreamResequencer processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.processor = null;
        this.streamProcessor = processor;
        this.expression = streamProcessor.getExpression().toString();
    }

    @Override
    public String getExpression() {
        return expression;
    }

    @Override
    public Integer getBatchSize() {
        if (processor != null) {
            return processor.getBatchSize();
        } else {
            return null;
        }
    }

    @Override
    public Long getTimeout() {
        if (processor != null) {
            return processor.getBatchTimeout();
        } else {
            return streamProcessor.getTimeout();
        }
    }

    @Override
    public Boolean isAllowDuplicates() {
        if (processor != null) {
            return processor.isAllowDuplicates();
        } else {
            return null;
        }
    }

    @Override
    public Boolean isReverse() {
        if (processor != null) {
            return processor.isReverse();
        } else {
            return null;
        }
    }

    @Override
    public Boolean isIgnoreInvalidExchanges() {
        if (processor != null) {
            return processor.isIgnoreInvalidExchanges();
        } else {
            return streamProcessor.isIgnoreInvalidExchanges();
        }
    }

    @Override
    public Integer getCapacity() {
        if (processor != null) {
            return null;
        } else {
            return streamProcessor.getCapacity();
        }
    }

    @Override
    public Boolean isRejectOld() {
        if (processor != null) {
            return null;
        } else {
            return streamProcessor.isRejectOld();
        }
    }
}
