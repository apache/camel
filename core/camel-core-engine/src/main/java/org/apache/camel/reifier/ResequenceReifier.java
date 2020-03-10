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
package org.apache.camel.reifier;

import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.config.ResequencerConfig;
import org.apache.camel.model.config.StreamResequencerConfig;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.Resequencer;
import org.apache.camel.processor.StreamResequencer;
import org.apache.camel.processor.resequencer.DefaultExchangeComparator;
import org.apache.camel.processor.resequencer.ExpressionResultComparator;
import org.apache.camel.util.ObjectHelper;

public class ResequenceReifier extends ProcessorReifier<ResequenceDefinition> {

    public ResequenceReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (ResequenceDefinition)definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        // if configured from XML then streamConfig has been set with the configuration
        ResequencerConfig resequencer = definition.getResequencerConfig();
        StreamResequencerConfig stream = definition.getStreamConfig();
        BatchResequencerConfig batch = definition.getBatchConfig();
        if (resequencer instanceof StreamResequencerConfig) {
            stream = (StreamResequencerConfig) resequencer;
        } else if (resequencer instanceof BatchResequencerConfig) {
            batch = (BatchResequencerConfig) resequencer;
        }

        if (stream != null) {
            return createStreamResequencer(stream);
        } else {
            // default as batch mode
            if (batch == null) {
                batch = BatchResequencerConfig.getDefault();
            }
            return createBatchResequencer(batch);
        }
    }

    /**
     * Creates a batch {@link Resequencer} instance applying the given
     * <code>config</code>.
     *
     * @param config batch resequencer configuration.
     * @return the configured batch resequencer.
     * @throws Exception can be thrown
     */
    @SuppressWarnings("deprecation")
    protected Resequencer createBatchResequencer(BatchResequencerConfig config) throws Exception {
        Processor processor = this.createChildProcessor(true);
        Expression expression = createExpression(definition.getExpression());

        // and wrap in unit of work
        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, processor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, camelContext));

        ObjectHelper.notNull(config, "config", this);
        ObjectHelper.notNull(expression, "expression", this);

        boolean isReverse = parseBoolean(config.getReverse(), false);
        boolean isAllowDuplicates = parseBoolean(config.getAllowDuplicates(), false);

        Resequencer resequencer = new Resequencer(camelContext, internal, expression, isAllowDuplicates, isReverse);
        resequencer.setBatchSize(parseInt(config.getBatchSize()));
        resequencer.setBatchTimeout(parseLong(config.getBatchTimeout()));
        resequencer.setReverse(isReverse);
        resequencer.setAllowDuplicates(isAllowDuplicates);
        if (config.getIgnoreInvalidExchanges() != null) {
            resequencer.setIgnoreInvalidExchanges(parseBoolean(config.getIgnoreInvalidExchanges(), false));
        }
        return resequencer;
    }

    /**
     * Creates a {@link StreamResequencer} instance applying the given
     * <code>config</code>.
     *
     * @param config stream resequencer configuration.
     * @return the configured stream resequencer.
     * @throws Exception can be thrwon
     */
    protected StreamResequencer createStreamResequencer(StreamResequencerConfig config) throws Exception {
        Processor processor = this.createChildProcessor(true);
        Expression expression = createExpression(definition.getExpression());

        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, processor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, camelContext));

        ObjectHelper.notNull(config, "config", this);
        ObjectHelper.notNull(expression, "expression", this);

        ExpressionResultComparator comparator;
        if (config.getComparatorRef() != null) {
            comparator = mandatoryLookup(config.getComparatorRef(), ExpressionResultComparator.class);
        } else {
            comparator = config.getComparator();
            if (comparator == null) {
                comparator = new DefaultExchangeComparator();
            }
        }
        comparator.setExpression(expression);

        StreamResequencer resequencer = new StreamResequencer(camelContext, internal, comparator, expression);
        resequencer.setTimeout(parseLong(config.getTimeout()));
        if (config.getDeliveryAttemptInterval() != null) {
            resequencer.setDeliveryAttemptInterval(parseLong(config.getDeliveryAttemptInterval()));
        }
        resequencer.setCapacity(parseInt(config.getCapacity()));
        resequencer.setRejectOld(parseBoolean(config.getRejectOld(), false));
        if (config.getIgnoreInvalidExchanges() != null) {
            resequencer.setIgnoreInvalidExchanges(parseBoolean(config.getIgnoreInvalidExchanges(), false));
        }
        return resequencer;
    }

}
