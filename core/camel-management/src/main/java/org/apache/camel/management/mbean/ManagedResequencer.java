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

    private final String expression;

    public ManagedResequencer(CamelContext context, Resequencer processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.expression = processor.getExpression().toString();
    }

    public ManagedResequencer(CamelContext context, StreamResequencer processor, ProcessorDefinition<?> definition) {
        super(context, processor, definition);
        this.expression = processor.getExpression().toString();
    }

    @Override
    public String getExpression() {
        return expression;
    }

    private Resequencer getResequencer() {
        if (getProcessor() instanceof Resequencer r) {
            return r;
        }
        return null;
    }

    private StreamResequencer getStreamResequencer() {
        if (getProcessor() instanceof StreamResequencer r) {
            return r;
        }
        return null;
    }

    @Override
    public Integer getBatchSize() {
        if (getResequencer() != null) {
            return getResequencer().getBatchSize();
        } else {
            return null;
        }
    }

    @Override
    public Long getTimeout() {
        if (getResequencer() != null) {
            return getResequencer().getBatchTimeout();
        } else {
            return getStreamResequencer().getTimeout();
        }
    }

    @Override
    public Boolean isAllowDuplicates() {
        if (getResequencer() != null) {
            return getResequencer().isAllowDuplicates();
        } else {
            return null;
        }
    }

    @Override
    public Boolean isReverse() {
        if (getResequencer() != null) {
            return getResequencer().isReverse();
        } else {
            return null;
        }
    }

    @Override
    public Boolean isIgnoreInvalidExchanges() {
        if (getResequencer() != null) {
            return getResequencer().isIgnoreInvalidExchanges();
        } else {
            return getStreamResequencer().isIgnoreInvalidExchanges();
        }
    }

    @Override
    public Integer getCapacity() {
        if (getResequencer() != null) {
            return null;
        } else {
            return getStreamResequencer().getCapacity();
        }
    }

    @Override
    public Boolean isRejectOld() {
        if (getResequencer() != null) {
            return null;
        } else {
            return getStreamResequencer().isRejectOld();
        }
    }
}
