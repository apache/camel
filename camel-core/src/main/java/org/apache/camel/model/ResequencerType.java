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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.config.StreamResequencerConfig;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Resequencer;
import org.apache.camel.processor.StreamResequencer;
import org.apache.camel.processor.resequencer.ExpressionResultComparator;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;resequence/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "resequence")
public class ResequencerType extends ProcessorType<ProcessorType> {
    @XmlElementRef
    private List<ExpressionType> expressions = new ArrayList<ExpressionType>();
    @XmlElementRef
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();
    // Binding annotation at setter
    private BatchResequencerConfig batchConfig;
    // Binding annotation at setter
    private StreamResequencerConfig streamConfig;
    @XmlTransient
    private List<Expression> expressionList;

    public ResequencerType() {
        this(null);
    }

    public ResequencerType(List<Expression> expressions) {
        this.expressionList = expressions;
        this.batch();
    }

    @Override
    public String getShortName() {
        return "resequencer";
    }
    
    // Fluent API
    // -------------------------------------------------------------------------
    /**
     * Configures the stream-based resequencing algorithm using the default
     * configuration.
     *
     * @return the builder
     */
    public ResequencerType stream() {
        return stream(StreamResequencerConfig.getDefault());
    }

    /**
     * Configures the batch-based resequencing algorithm using the default
     * configuration.
     *
     * @return the builder
     */
    public ResequencerType batch() {
        return batch(BatchResequencerConfig.getDefault());
    }

    /**
     * Configures the stream-based resequencing algorithm using the given
     * {@link StreamResequencerConfig}.
     *
     * @param config  the config
     * @return the builder
     */
    public ResequencerType stream(StreamResequencerConfig config) {
        this.streamConfig = config;
        this.batchConfig = null;
        return this;
    }

    /**
     * Configures the batch-based resequencing algorithm using the given
     * {@link BatchResequencerConfig}.
     *
     * @param config  the config
     * @return the builder
     */
    public ResequencerType batch(BatchResequencerConfig config) {
        this.batchConfig = config;
        this.streamConfig = null;
        return this;
    }

    /**
     * Sets the expression to use for reordering
     *
     * @param expression  the expression
     * @return the builder
     */
    public ResequencerType expression(ExpressionType expression) {
        expressions.add(expression);
        return this;
    }

    /**
     * Sets the timeout
     * @param timeout  timeout in millis
     * @return the builder
     */
    public ResequencerType timeout(long timeout) {
        if (batchConfig != null) {
            batchConfig.setBatchTimeout(timeout);
        } else {
            streamConfig.setTimeout(timeout);
        }
        return this;
    }

    /**
     * Sets the in batch size for number of exchanges received
     * @param batchSize  the batch size
     * @return the builder
     */
    public ResequencerType size(int batchSize) {
        if (batchConfig == null) {
            throw new IllegalStateException("size() only supported for batch resequencer");
        }
        batchConfig.setBatchSize(batchSize);
        return this;
    }

    /**
     * Sets the capacity for the stream resequencer
     *
     * @param capacity  the capacity
     * @return the builder
     */
    public ResequencerType capacity(int capacity) {
        if (streamConfig == null) {
            throw new IllegalStateException("capacity() only supported for stream resequencer");
        }
        streamConfig.setCapacity(capacity);
        return this;

    }

    /**
     * Sets the comparator to use for stream resequencer
     *
     * @param comparator  the comparator
     * @return the builder
     */
    public ResequencerType comparator(ExpressionResultComparator comparator) {
        if (streamConfig == null) {
            throw new IllegalStateException("comparator() only supported for stream resequencer");
        }
        streamConfig.setComparator(comparator);
        return this;
    }

    @Override
    public String toString() {
        return "Resequencer[" + getExpressions() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getLabel() {
        return ExpressionType.getLabel(getExpressions());
    }

    public List<ExpressionType> getExpressions() {
        return expressions;
    }

    public List<ProcessorType> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType> outputs) {
        this.outputs = outputs;
    }

    public BatchResequencerConfig getBatchConfig() {
        return batchConfig;
    }

    public BatchResequencerConfig getBatchConfig(BatchResequencerConfig defaultConfig) {
        return batchConfig;
    }

    public StreamResequencerConfig getStreamConfig() {
        return streamConfig;
    }

    @XmlElement(name = "batch-config", required = false)
    public void setBatchConfig(BatchResequencerConfig batchConfig) {
        // TODO: find out how to have these two within an <xsd:choice>
        batch(batchConfig);
    }

    @XmlElement(name = "stream-config", required = false)
    public void setStreamConfig(StreamResequencerConfig streamConfig) {
        // TODO: find out how to have these two within an <xsd:choice>
        stream(streamConfig);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (batchConfig != null) {
            return createBatchResequencer(routeContext, batchConfig);
        } else {
            // streamConfig should be non-null if batchConfig is null
            return createStreamResequencer(routeContext, streamConfig);
        }
    }

    /**
     * Creates a batch {@link Resequencer} instance applying the given
     * <code>config</code>.
     * 
     * @param routeContext
     *            route context.
     * @param config
     *            batch resequencer configuration.
     * @return the configured batch resequencer.
     * @throws Exception 
     */
    protected Resequencer createBatchResequencer(RouteContext routeContext,
            BatchResequencerConfig config) throws Exception {
        Processor processor = routeContext.createProcessor(this);
        Resequencer resequencer = new Resequencer(processor, resolveExpressionList(routeContext));
        resequencer.setBatchSize(config.getBatchSize());
        resequencer.setBatchTimeout(config.getBatchTimeout());
        return resequencer;
    }

    /**
     * Creates a {@link StreamResequencer} instance applying the given
     * <code>config</code>.
     * 
     * @param routeContext
     *            route context.
     * @param config
     *            stream resequencer configuration.
     * @return the configured stream resequencer.
     * @throws Exception
     */
    protected StreamResequencer createStreamResequencer(RouteContext routeContext, 
            StreamResequencerConfig config) throws Exception {
        config.getComparator().setExpressions(resolveExpressionList(routeContext));
        Processor processor = routeContext.createProcessor(this);
        StreamResequencer resequencer = new StreamResequencer(processor, config.getComparator());
        resequencer.setTimeout(config.getTimeout());
        resequencer.setCapacity(config.getCapacity());
        return resequencer;
        
    }

    private List<Expression> resolveExpressionList(RouteContext routeContext) {
        if (expressionList == null) {
            expressionList = new ArrayList<Expression>();
            for (ExpressionType expression : expressions) {
                expressionList.add(expression.createExpression(routeContext));
            }
        }
        if (expressionList.isEmpty()) {
            throw new IllegalArgumentException("No expressions configured for: " + this);
        }
        return expressionList;
    }
}
