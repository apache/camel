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

import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedAggregateProcessorMBean;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.processor.aggregate.AggregateProcessor;

@ManagedResource(description = "Managed AggregateProcessor")
public class ManagedAggregateProcessor extends ManagedProcessor implements ManagedAggregateProcessorMBean {
    private final AggregateProcessor processor;

    public ManagedAggregateProcessor(CamelContext context, AggregateProcessor processor, AggregateDefinition definition) {
        super(context, processor, definition);
        this.processor = processor;
    }

    @Override
    public AggregateProcessor getProcessor() {
        return processor;
    }

    @Override
    public AggregateDefinition getDefinition() {
        return (AggregateDefinition) super.getDefinition();
    }

    @Override
    public String getCorrelationExpressionLanguage() {
        if (getDefinition().getCorrelationExpression() != null) {
            return getDefinition().getCorrelationExpression().getExpressionType().getLanguage();
        } else {
            return null;
        }
    }

    @Override
    public String getCorrelationExpression() {
        if (getDefinition().getCorrelationExpression() != null) {
            return getDefinition().getCorrelationExpression().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    @Override
    public long getCompletionTimeout() {
        return processor.getCompletionTimeout();
    }

    @Override
    public String getCompletionTimeoutLanguage() {
        if (getDefinition().getCompletionTimeoutExpression() != null) {
            return getDefinition().getCompletionTimeoutExpression().getExpressionType().getLanguage();
        } else {
            return null;
        }
    }

    @Override
    public String getCompletionTimeoutExpression() {
        if (getDefinition().getCompletionTimeoutExpression() != null) {
            return getDefinition().getCompletionTimeoutExpression().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    @Override
    public long getCompletionInterval() {
        return processor.getCompletionInterval();
    }

    @Override
    public long getCompletionTimeoutCheckerInterval() {
        return processor.getCompletionTimeoutCheckerInterval();
    }

    @Override
    public int getCompletionSize() {
        return processor.getCompletionSize();
    }

    @Override
    public String getCompletionSizeExpressionLanguage() {
        if (getDefinition().getCompletionSizeExpression() != null) {
            return getDefinition().getCompletionSizeExpression().getExpressionType().getLanguage();
        } else {
            return null;
        }
    }

    @Override
    public String getCompletionSizeExpression() {
        if (getDefinition().getCompletionSizeExpression() != null) {
            return getDefinition().getCompletionSizeExpression().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    @Override
    public boolean isCompletionFromBatchConsumer() {
        return processor.isCompletionFromBatchConsumer();
    }

    @Override
    public boolean isCompletionOnNewCorrelationGroup() {
        return processor.isCompletionOnNewCorrelationGroup();
    }

    @Override
    public boolean isIgnoreInvalidCorrelationKeys() {
        return processor.isIgnoreInvalidCorrelationKeys();
    }

    @Override
    public Integer getCloseCorrelationKeyOnCompletion() {
        return processor.getCloseCorrelationKeyOnCompletion();
    }

    @Override
    public boolean isParallelProcessing() {
        return processor.isParallelProcessing();
    }

    @Override
    public boolean isOptimisticLocking() {
        return processor.isOptimisticLocking();
    }

    @Override
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

    @Override
    public String getCompletionPredicate() {
        if (getDefinition().getCompletionPredicate() != null) {
            return getDefinition().getCompletionPredicate().getExpressionType().getExpression();
        } else {
            return null;
        }
    }

    @Override
    public boolean isDiscardOnCompletionTimeout() {
        return processor.isDiscardOnCompletionTimeout();
    }

    @Override
    public boolean isForceCompletionOnStop() {
        return processor.isCompletionFromBatchConsumer();
    }

    @Override
    public boolean isCompleteAllOnStop() {
        return processor.isCompleteAllOnStop();
    }

    @Override
    public int getInProgressCompleteExchanges() {
        return processor.getInProgressCompleteExchanges();
    }

    @Override
    public int aggregationRepositoryGroups() {
        Set<String> keys = processor.getAggregationRepository().getKeys();
        if (keys != null) {
            return keys.size();
        } else {
            return 0;
        }
    }

    @Override
    public int forceCompletionOfGroup(String key) {
        if (processor.getAggregateController() != null) {
            return processor.getAggregateController().forceCompletionOfGroup(key);
        } else {
            return 0;
        }
    }

    @Override
    public int forceCompletionOfAllGroups() {
        if (processor.getAggregateController() != null) {
            return processor.getAggregateController().forceCompletionOfAllGroups();
        } else {
            return 0;
        }
    }

    @Override
    public int forceDiscardingOfGroup(String key) {
        if (processor.getAggregateController() != null) {
            return processor.getAggregateController().forceDiscardingOfGroup(key);
        } else {
            return 0;
        }
    }

    @Override
    public int forceDiscardingOfAllGroups() {
        if (processor.getAggregateController() != null) {
            return processor.getAggregateController().forceDiscardingOfAllGroups();
        } else {
            return 0;
        }
    }

    @Override
    public int getClosedCorrelationKeysCacheSize() {
        return processor.getClosedCorrelationKeysCacheSize();
    }

    @Override
    public void clearClosedCorrelationKeysCache() {
        processor.clearClosedCorrelationKeysCache();
    }

    @Override
    public long getTotalIn() {
        return processor.getStatistics().getTotalIn();
    }

    @Override
    public long getTotalCompleted() {
        return processor.getStatistics().getTotalCompleted();
    }

    @Override
    public long getCompletedBySize() {
        return processor.getStatistics().getCompletedBySize();
    }

    @Override
    public long getCompletedByStrategy() {
        return processor.getStatistics().getCompletedByStrategy();
    }

    @Override
    public long getCompletedByInterval() {
        return processor.getStatistics().getCompletedByInterval();
    }

    @Override
    public long getCompletedByTimeout() {
        return processor.getStatistics().getCompletedByTimeout();
    }

    @Override
    public long getCompletedByPredicate() {
        return processor.getStatistics().getCompletedByPredicate();
    }

    @Override
    public long getCompletedByBatchConsumer() {
        return processor.getStatistics().getCompletedByBatchConsumer();
    }

    @Override
    public long getCompletedByForce() {
        return processor.getStatistics().getCompletedByForce();
    }

    @Override
    public long getDiscarded() {
        return processor.getStatistics().getDiscarded();
    }

    @Override
    public void resetStatistics() {
        processor.getStatistics().reset();
    }
}
