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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.config.ResequencerConfig;
import org.apache.camel.model.config.StreamResequencerConfig;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.Resequencer;
import org.apache.camel.processor.StreamResequencer;
import org.apache.camel.processor.resequencer.ExpressionResultComparator;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Resequences (re-order) messages based on an expression
 *
 * @version 
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "resequence")
@XmlAccessorType(XmlAccessType.FIELD)
public class ResequenceDefinition extends ProcessorDefinition<ResequenceDefinition> {
    @Metadata(required = "false")
    @XmlElements({
        @XmlElement(name = "batch-config", type = BatchResequencerConfig.class),
        @XmlElement(name = "stream-config", type = StreamResequencerConfig.class)}
        )
    private ResequencerConfig resequencerConfig;
    @XmlTransient
    private BatchResequencerConfig batchConfig;
    @XmlTransient
    private StreamResequencerConfig streamConfig;
    @XmlElementRef @Metadata(required = "true")
    private ExpressionDefinition expression;
    @XmlElementRef
    private List<ProcessorDefinition<?>> outputs = new ArrayList<ProcessorDefinition<?>>();

    public ResequenceDefinition() {
    }

    public ResequenceDefinition(Expression expression) {
        if (expression != null) {
            setExpression(ExpressionNodeHelper.toExpressionDefinition(expression));
        }
    }

    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        this.outputs = outputs;
    }

    @Override
    public boolean isOutputSupported() {
        return true;
    }

    // Fluent API
    // -------------------------------------------------------------------------
    /**
     * Configures the stream-based resequencing algorithm using the default
     * configuration.
     *
     * @return the builder
     */
    public ResequenceDefinition stream() {
        return stream(StreamResequencerConfig.getDefault());
    }

    /**
     * Configures the batch-based resequencing algorithm using the default
     * configuration.
     *
     * @return the builder
     */
    public ResequenceDefinition batch() {
        return batch(BatchResequencerConfig.getDefault());
    }

    /**
     * Configures the stream-based resequencing algorithm using the given
     * {@link StreamResequencerConfig}.
     *
     * @param config  the config
     * @return the builder
     */
    public ResequenceDefinition stream(StreamResequencerConfig config) {
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
    public ResequenceDefinition batch(BatchResequencerConfig config) {
        this.batchConfig = config;
        this.streamConfig = null;
        return this;
    }

    /**
     * Sets the timeout
     * @param timeout  timeout in millis
     * @return the builder
     */
    public ResequenceDefinition timeout(long timeout) {
        if (streamConfig != null) {
            streamConfig.setTimeout(timeout);
        } else {
            // initialize batch mode as its default mode
            if (batchConfig == null) {
                batch();
            }
            batchConfig.setBatchTimeout(timeout);
        }
        return this;
    }

    /**
     * Sets the interval in milli seconds the stream resequencer will at most wait
     * while waiting for condition of being able to deliver.
     *
     * @param deliveryAttemptInterval  interval in millis
     * @return the builder
     */
    public ResequenceDefinition deliveryAttemptInterval(long deliveryAttemptInterval) {
        if (streamConfig == null) {
            throw new IllegalStateException("deliveryAttemptInterval() only supported for stream resequencer");
        }
        streamConfig.setDeliveryAttemptInterval(deliveryAttemptInterval);
        return this;
    }

    /**
     * Sets the rejectOld flag to throw an error when a message older than the last delivered message is processed
     * @return the builder
     */
    public ResequenceDefinition rejectOld() {
        if (streamConfig == null) {
            throw new IllegalStateException("rejectOld() only supported for stream resequencer");
        }
        streamConfig.setRejectOld(true);
        return this;
    }

    /**
     * Sets the in batch size for number of exchanges received
     * @param batchSize  the batch size
     * @return the builder
     */
    public ResequenceDefinition size(int batchSize) {
        if (streamConfig != null) {
            throw new IllegalStateException("size() only supported for batch resequencer");
        }
        // initialize batch mode as its default mode
        if (batchConfig == null) {
            batch();
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
    public ResequenceDefinition capacity(int capacity) {
        if (streamConfig == null) {
            throw new IllegalStateException("capacity() only supported for stream resequencer");
        }
        streamConfig.setCapacity(capacity);
        return this;

    }

    /**
     * Enables duplicates for the batch resequencer mode
     * @return the builder
     */
    public ResequenceDefinition allowDuplicates() {
        if (streamConfig != null) {
            throw new IllegalStateException("allowDuplicates() only supported for batch resequencer");
        }
        // initialize batch mode as its default mode
        if (batchConfig == null) {
            batch();
        }
        batchConfig.setAllowDuplicates(true);
        return this;
    }

    /**
     * Enables reverse mode for the batch resequencer mode.
     * <p/>
     * This means the expression for determine the sequence order will be reversed.
     * Can be used for Z..A or 9..0 ordering.
     *
     * @return the builder
     */
    public ResequenceDefinition reverse() {
        if (streamConfig != null) {
            throw new IllegalStateException("reverse() only supported for batch resequencer");
        }
        // initialize batch mode as its default mode
        if (batchConfig == null) {
            batch();
        }
        batchConfig.setReverse(true);
        return this;
    }

    /**
     * If an incoming {@link org.apache.camel.Exchange} is invalid, then it will be ignored.
     *
     * @return builder
     */
    public ResequenceDefinition ignoreInvalidExchanges() {
        if (streamConfig != null) {
            streamConfig.setIgnoreInvalidExchanges(true);
        } else {
            // initialize batch mode as its default mode
            if (batchConfig == null) {
                batch();
            }
            batchConfig.setIgnoreInvalidExchanges(true);
        }
        return this;
    }

    /**
     * Sets the comparator to use for stream resequencer
     *
     * @param comparator  the comparator
     * @return the builder
     */
    public ResequenceDefinition comparator(ExpressionResultComparator comparator) {
        if (streamConfig == null) {
            throw new IllegalStateException("comparator() only supported for stream resequencer");
        }
        streamConfig.setComparator(comparator);
        return this;
    }

    @Override
    public String toString() {
        return "Resequencer[" + getExpression() + " -> " + getOutputs() + "]";
    }
    
    @Override
    public String getLabel() {
        return "resequencer[" + (getExpression() != null ? getExpression().getLabel() : "") + "]";
    }

    public ResequencerConfig getResequencerConfig() {
        return resequencerConfig;
    }

    /**
     * To configure the resequencer in using either batch or stream configuration. Will by default use batch configuration.
     */
    public void setResequencerConfig(ResequencerConfig resequencerConfig) {
        this.resequencerConfig = resequencerConfig;
    }

    public BatchResequencerConfig getBatchConfig() {
        if (batchConfig == null && resequencerConfig != null && resequencerConfig instanceof BatchResequencerConfig) {
            return (BatchResequencerConfig) resequencerConfig;
        }
        return batchConfig;
    }

    public StreamResequencerConfig getStreamConfig() {
        if (streamConfig == null && resequencerConfig != null && resequencerConfig instanceof StreamResequencerConfig) {
            return (StreamResequencerConfig) resequencerConfig;
        }
        return streamConfig;
    }

    public void setBatchConfig(BatchResequencerConfig batchConfig) {
        this.batchConfig = batchConfig;
    }

    public void setStreamConfig(StreamResequencerConfig streamConfig) {
        this.streamConfig = streamConfig;
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    /**
     * Expression to use for re-ordering the messages, such as a header with a sequence number
     */
    public void setExpression(ExpressionDefinition expression) {
        this.expression = expression;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // if configured from XML then streamConfig has been set with the configuration
        if (resequencerConfig != null) {
            if (resequencerConfig instanceof StreamResequencerConfig) {
                streamConfig = (StreamResequencerConfig) resequencerConfig;
            } else {
                batchConfig = (BatchResequencerConfig) resequencerConfig;
            }
        }

        if (streamConfig != null) {
            return createStreamResequencer(routeContext, streamConfig);
        } else {
            if (batchConfig == null) {
                // default as batch mode
                batch();
            }
            return createBatchResequencer(routeContext, batchConfig);
        }
    }

    /**
     * Creates a batch {@link Resequencer} instance applying the given <code>config</code>.
     * 
     * @param routeContext route context.
     * @param config batch resequencer configuration.
     * @return the configured batch resequencer.
     * @throws Exception can be thrown
     */
    @SuppressWarnings("deprecation")
    protected Resequencer createBatchResequencer(RouteContext routeContext,
                                                 BatchResequencerConfig config) throws Exception {
        Processor processor = this.createChildProcessor(routeContext, true);
        Expression expression = getExpression().createExpression(routeContext);

        // and wrap in unit of work
        CamelInternalProcessor internal = new CamelInternalProcessor(processor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));

        ObjectHelper.notNull(config, "config", this);
        ObjectHelper.notNull(expression, "expression", this);

        boolean isReverse = config.getReverse() != null && config.getReverse();
        boolean isAllowDuplicates = config.getAllowDuplicates() != null && config.getAllowDuplicates();

        Resequencer resequencer = new Resequencer(routeContext.getCamelContext(), internal, expression, isAllowDuplicates, isReverse);
        resequencer.setBatchSize(config.getBatchSize());
        resequencer.setBatchTimeout(config.getBatchTimeout());
        resequencer.setReverse(isReverse);
        resequencer.setAllowDuplicates(isAllowDuplicates);
        if (config.getIgnoreInvalidExchanges() != null) {
            resequencer.setIgnoreInvalidExchanges(config.getIgnoreInvalidExchanges());
        }
        return resequencer;
    }

    /**
     * Creates a {@link StreamResequencer} instance applying the given <code>config</code>.
     * 
     * @param routeContext route context.
     * @param config stream resequencer configuration.
     * @return the configured stream resequencer.
     * @throws Exception can be thrwon
     */
    protected StreamResequencer createStreamResequencer(RouteContext routeContext,
                                                        StreamResequencerConfig config) throws Exception {
        Processor processor = this.createChildProcessor(routeContext, true);
        Expression expression = getExpression().createExpression(routeContext);

        CamelInternalProcessor internal = new CamelInternalProcessor(processor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));

        ObjectHelper.notNull(config, "config", this);
        ObjectHelper.notNull(expression, "expression", this);

        ExpressionResultComparator comparator;
        if (config.getComparatorRef() != null) {
            comparator = CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), config.getComparatorRef(), ExpressionResultComparator.class);
        } else {
            comparator = config.getComparator();
        }
        comparator.setExpression(expression);

        StreamResequencer resequencer = new StreamResequencer(routeContext.getCamelContext(), internal, comparator, expression);
        resequencer.setTimeout(config.getTimeout());
        if (config.getDeliveryAttemptInterval() != null) {
            resequencer.setDeliveryAttemptInterval(config.getDeliveryAttemptInterval());
        }
        resequencer.setCapacity(config.getCapacity());
        resequencer.setRejectOld(config.getRejectOld());
        if (config.getIgnoreInvalidExchanges() != null) {
            resequencer.setIgnoreInvalidExchanges(config.getIgnoreInvalidExchanges());
        }
        return resequencer;
    }

}
