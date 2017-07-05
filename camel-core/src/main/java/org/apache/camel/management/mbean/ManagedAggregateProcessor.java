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

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedAggregateProcessorMBean;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.spi.ManagementStrategy;

/**
 * @version 
 */
@ManagedResource(description = "Managed AggregateProcessor")
public class ManagedAggregateProcessor extends ManagedProcessor implements ManagedAggregateProcessorMBean {
    private final AggregateProcessor processor;

    public ManagedAggregateProcessor(CamelContext context, AggregateProcessor processor, AggregateDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    public void init(ManagementStrategy strategy) {
        super.init(strategy);
    }

    public AggregateProcessor getProcessor() {
        return processor;
    }

    @Override
    public AggregateDefinition getDefinition() {
        return (AggregateDefinition) super.getDefinition();
    }

    public String getCorrelationExpressionLanguage() {
        if (getDefinition().getCorrelationExpression() != null) {
            return getDefinition().getCorrelationExpression().getExpressionType().getLanguage();
        } else {
            return null;
        }
    }

    public String getCorrelationExpression() {
        if (getDefinition().getCorrelationExpression() != null) {
            return getDefinition().getCorrelationExpression().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    public long getCompletionTimeout() {
        return processor.getCompletionTimeout();
    }

    public String getCompletionTimeoutLanguage() {
        if (getDefinition().getCompletionTimeoutExpression() != null) {
            return getDefinition().getCompletionTimeoutExpression().getExpressionType().getLanguage();
        } else {
            return null;
        }
    }

    public String getCompletionTimeoutExpression() {
        if (getDefinition().getCompletionTimeoutExpression() != null) {
            return getDefinition().getCompletionTimeoutExpression().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    public long getCompletionInterval() {
        return processor.getCompletionInterval();
    }

    public long getCompletionTimeoutCheckerInterval() {
        return processor.getCompletionTimeoutCheckerInterval();
    }

    public int getCompletionSize() {
        return processor.getCompletionSize();
    }

    public String getCompletionSizeExpressionLanguage() {
        if (getDefinition().getCompletionSizeExpression() != null) {
            return getDefinition().getCompletionSizeExpression().getExpressionType().getLanguage();
        } else {
            return null;
        }
    }

    public String getCompletionSizeExpression() {
        if (getDefinition().getCompletionSizeExpression() != null) {
            return getDefinition().getCompletionSizeExpression().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    public boolean isCompletionFromBatchConsumer() {
        return processor.isCompletionFromBatchConsumer();
    }

    public boolean isIgnoreInvalidCorrelationKeys() {
        return processor.isIgnoreInvalidCorrelationKeys();
    }

    public Integer getCloseCorrelationKeyOnCompletion() {
        return processor.getCloseCorrelationKeyOnCompletion();
    }

    public boolean isParallelProcessing() {
        return processor.isParallelProcessing();
    }

    public boolean isOptimisticLocking() {
        return processor.isOptimisticLocking();
    }

    public boolean isEagerCheckCompletion() {
        return processor.isEagerCheckCompletion();
    }

    @Override
    public String getCompletionPredicateLanguage() {
        if (getDefinition().getCompletionPredicate() != null) {
            return getDefinition().getCompletionPredicate().getExpressionType().getLanguage();
        } else {
            return null;
        }
    }

    public String getCompletionPredicate() {
        if (getDefinition().getCompletionPredicate() != null) {
            return getDefinition().getCompletionPredicate().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    public boolean isDiscardOnCompletionTimeout() {
        return processor.isDiscardOnCompletionTimeout();
    }

    public boolean isForceCompletionOnStop() {
        return processor.isCompletionFromBatchConsumer();
    }

    public boolean isCompleteAllOnStop() {
        return processor.isCompleteAllOnStop();
    }

    public int getInProgressCompleteExchanges() {
        return processor.getInProgressCompleteExchanges();
    }

    public int aggregationRepositoryGroups() {
        Set<String> keys = processor.getAggregationRepository().getKeys();
        if (keys != null) {
            return keys.size();
        } else {
            return 0;
        }
    }

    public int forceCompletionOfGroup(String key) {
        if (processor.getAggregateController() != null) {
            return processor.getAggregateController().forceCompletionOfGroup(key);
        } else {
            return 0;
        }
    }

    public int forceCompletionOfAllGroups() {
        if (processor.getAggregateController() != null) {
            return processor.getAggregateController().forceCompletionOfAllGroups();
        } else {
            return 0;
        }
    }

    public int getClosedCorrelationKeysCacheSize() {
        return processor.getClosedCorrelationKeysCacheSize();
    }

    public void clearClosedCorrelationKeysCache() {
        processor.clearClosedCorrelationKeysCache();
    }

    public long getTotalIn() {
        return processor.getStatistics().getTotalIn();
    }

    public long getTotalCompleted() {
        return processor.getStatistics().getTotalCompleted();
    }

    public long getCompletedBySize() {
        return processor.getStatistics().getCompletedBySize();
    }

    public long getCompletedByStrategy() {
        return processor.getStatistics().getCompletedByStrategy();
    }

    public long getCompletedByInterval() {
        return processor.getStatistics().getCompletedByInterval();
    }

    public long getCompletedByTimeout() {
        return processor.getStatistics().getCompletedByTimeout();
    }

    public long getCompletedByPredicate() {
        return processor.getStatistics().getCompletedByPredicate();
    }

    public long getCompletedByBatchConsumer() {
        return processor.getStatistics().getCompletedByBatchConsumer();
    }

    public long getCompletedByForce() {
        return processor.getStatistics().getCompletedByForce();
    }

    public void resetStatistics() {
        processor.getStatistics().reset();
    }
}
