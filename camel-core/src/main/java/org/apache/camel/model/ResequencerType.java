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
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.config.StreamResequencerConfig;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Resequencer;
import org.apache.camel.processor.StreamResequencer;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;resequencer/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "resequencer")
public class ResequencerType extends ProcessorType<ProcessorType> {
    @XmlElementRef
    private List<ExpressionType> expressions = new ArrayList<ExpressionType>();
    @XmlElementRef
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();
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

    /**
     * Configures the stream-based resequencing algorithm using the default
     * configuration.
     *
     * @return <code>this</code> instance.
     */
    public ResequencerType stream() {
        return stream(StreamResequencerConfig.getDefault());
    }

    /**
     * Configures the batch-based resequencing algorithm using the default
     * configuration.
     *
     * @return <code>this</code> instance.
     */
    public ResequencerType batch() {
        return batch(BatchResequencerConfig.getDefault());
    }

    /**
     * Configures the stream-based resequencing algorithm using the given
     * {@link StreamResequencerConfig}.
     *
     * @return <code>this</code> instance.
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
     * @return <code>this</code> instance.
     */
    public ResequencerType batch(BatchResequencerConfig config) {
        this.batchConfig = config;
        this.streamConfig = null;
        return this;
    }

    public ResequencerType expression(ExpressionType expression) {
        expressions.add(expression);
        return this;
    }

    @Override
    public String toString() {
        return "Resequencer[ " + getExpressions() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getLabel() {
        return ExpressionType.getLabel(getExpressions());
    }

    public List<ExpressionType> getExpressions() {
        return expressions;
    }

    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<ProcessorType<?>> outputs) {
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

    @Override
    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        if (batchConfig != null) {
            routes.add(createBatchResequencerRoute(routeContext));
        } else {
            // StreamResequencer created via createProcessor method
            super.addRoutes(routeContext, routes);
        }
    }

    private Route<? extends Exchange> createBatchResequencerRoute(RouteContext routeContext) throws Exception {
        final Resequencer resequencer = createBatchResequencer(routeContext, batchConfig);
        return new Route(routeContext.getEndpoint(), resequencer) {
            @Override
            public String toString() {
                return "BatchResequencerRoute[" + getEndpoint() + " -> " + resequencer.getProcessor() + "]";
            }
        };
    }

    protected Resequencer createBatchResequencer(RouteContext routeContext,
            BatchResequencerConfig config) throws Exception {
        Processor processor = routeContext.createProcessor(this);
        Resequencer resequencer = new Resequencer(routeContext.getEndpoint(),
                processor, resolveExpressionList(routeContext));
        resequencer.setBatchSize(config.getBatchSize());
        resequencer.setBatchTimeout(config.getBatchTimeout());
        return resequencer;
    }

    protected StreamResequencer createStreamResequencer(RouteContext routeContext,
            StreamResequencerConfig config) throws Exception {
        config.getComparator().setExpressions(resolveExpressionList(routeContext));
        Processor processor = routeContext.createProcessor(this);
        StreamResequencer resequencer = new StreamResequencer(processor,
                config.getComparator(), config.getCapacity());
        resequencer.setTimeout(config.getTimeout());
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
