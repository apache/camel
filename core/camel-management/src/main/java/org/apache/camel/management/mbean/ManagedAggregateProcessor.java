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

    public ManagedAggregateProcessor(
            CamelContext context, AggregateProcessor processor, AggregateDefinition definition) {
        super(context, processor, definition);
    }

    @Override
    public AggregateProcessor getProcessor() {
        return (AggregateProcessor) super.getProcessor();
    }

    @Override
    public AggregateDefinition getDefinition() {
        return (AggregateDefinition) super.getDefinition();
    }

    @Override
    public String getCorrelationExpressionLanguage() {
        if (getDefinition().getCorrelationExpression() != null) {
            return getDefinition()
                    .getCorrelationExpression()
                    .getExpressionType()
                    .getLanguage();
        } else {
            return null;
        }
    }

    @Override
    public String getCorrelationExpression() {
        if (getDefinition().getCorrelationExpression() != null) {
            return getDefinition()
                    .getCorrelationExpression()
                    .getExpressionType()
                    .getExpression();
        } else {
            return null;
        }
    }

    @Override
    public long getCompletionTimeout() {
        return getProcessor().getCompletionTimeout();
    }

    @Override
    public String getCompletionTimeoutLanguage() {
        if (getDefinition().getCompletionTimeoutExpression() != null) {
            return getDefinition()
                    .getCompletionTimeoutExpression()
                    .getExpressionType()
                    .getLanguage();
        } else {
            return null;
        }
    }

    @Override
    public String getCompletionTimeoutExpression() {
        if (getDefinition().getCompletionTimeoutExpression() != null) {
            return getDefinition()
                    .getCompletionTimeoutExpression()
                    .getExpressionType()
                    .getExpression();
        } else {
            return null;
        }
    }

    @Override
    public long getCompletionInterval() {
        return getProcessor().getCompletionInterval();
    }

    @Override
    public long getCompletionTimeoutCheckerInterval() {
        return getProcessor().getCompletionTimeoutCheckerInterval();
    }

    @Override
    public int getCompletionSize() {
        return getProcessor().getCompletionSize();
    }

    @Override
    public String getCompletionSizeExpressionLanguage() {
        if (getDefinition().getCompletionSizeExpression() != null) {
            return getDefinition()
                    .getCompletionSizeExpression()
                    .getExpressionType()
                    .getLanguage();
        } else {
            return null;
        }
    }

    @Override
    public String getCompletionSizeExpression() {
        if (getDefinition().getCompletionSizeExpression() != null) {
            return getDefinition()
                    .getCompletionSizeExpression()
                    .getExpressionType()
                    .getExpression();
        } else {
            return null;
        }
    }

    @Override
    public boolean isCompletionFromBatchConsumer() {
        return getProcessor().isCompletionFromBatchConsumer();
    }

    @Override
    public boolean isCompletionOnNewCorrelationGroup() {
        return getProcessor().isCompletionOnNewCorrelationGroup();
    }

    @Override
    public boolean isIgnoreInvalidCorrelationKeys() {
        return getProcessor().isIgnoreInvalidCorrelationKeys();
    }

    @Override
    public Integer getCloseCorrelationKeyOnCompletion() {
        return getProcessor().getCloseCorrelationKeyOnCompletion();
    }

    @Override
    public boolean isParallelProcessing() {
        return getProcessor().isParallelProcessing();
    }

    @Override
    public boolean isOptimisticLocking() {
        return getProcessor().isOptimisticLocking();
    }

    @Override
    public boolean isEagerCheckCompletion() {
        return getProcessor().isEagerCheckCompletion();
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
        return getProcessor().isDiscardOnCompletionTimeout();
    }

    @Override
    public boolean isForceCompletionOnStop() {
        return getProcessor().isCompletionFromBatchConsumer();
    }

    @Override
    public boolean isCompleteAllOnStop() {
        return getProcessor().isCompleteAllOnStop();
    }

    @Override
    public int getInProgressCompleteExchanges() {
        return getProcessor().getInProgressCompleteExchanges();
    }

    @Override
    public int aggregationRepositoryGroups() {
        Set<String> keys = getProcessor().getAggregationRepository().getKeys();
        if (keys != null) {
            return keys.size();
        } else {
            return 0;
        }
    }

    @Override
    public int forceCompletionOfGroup(String key) {
        if (getProcessor().getAggregateController() != null) {
            return getProcessor().getAggregateController().forceCompletionOfGroup(key);
        } else {
            return 0;
        }
    }

    @Override
    public int forceCompletionOfAllGroups() {
        if (getProcessor().getAggregateController() != null) {
            return getProcessor().getAggregateController().forceCompletionOfAllGroups();
        } else {
            return 0;
        }
    }

    @Override
    public int forceDiscardingOfGroup(String key) {
        if (getProcessor().getAggregateController() != null) {
            return getProcessor().getAggregateController().forceDiscardingOfGroup(key);
        } else {
            return 0;
        }
    }

    @Override
    public int forceDiscardingOfAllGroups() {
        if (getProcessor().getAggregateController() != null) {
            return getProcessor().getAggregateController().forceDiscardingOfAllGroups();
        } else {
            return 0;
        }
    }

    @Override
    public int getClosedCorrelationKeysCacheSize() {
        return getProcessor().getClosedCorrelationKeysCacheSize();
    }

    @Override
    public void clearClosedCorrelationKeysCache() {
        getProcessor().clearClosedCorrelationKeysCache();
    }

    @Override
    public long getTotalIn() {
        return getProcessor().getStatistics().getTotalIn();
    }

    @Override
    public long getTotalCompleted() {
        return getProcessor().getStatistics().getTotalCompleted();
    }

    @Override
    public long getCompletedBySize() {
        return getProcessor().getStatistics().getCompletedBySize();
    }

    @Override
    public long getCompletedByStrategy() {
        return getProcessor().getStatistics().getCompletedByStrategy();
    }

    @Override
    public long getCompletedByInterval() {
        return getProcessor().getStatistics().getCompletedByInterval();
    }

    @Override
    public long getCompletedByTimeout() {
        return getProcessor().getStatistics().getCompletedByTimeout();
    }

    @Override
    public long getCompletedByPredicate() {
        return getProcessor().getStatistics().getCompletedByPredicate();
    }

    @Override
    public long getCompletedByBatchConsumer() {
        return getProcessor().getStatistics().getCompletedByBatchConsumer();
    }

    @Override
    public long getCompletedByForce() {
        return getProcessor().getStatistics().getCompletedByForce();
    }

    @Override
    public long getDiscarded() {
        return getProcessor().getStatistics().getDiscarded();
    }

    @Override
    public void resetStatistics() {
        getProcessor().getStatistics().reset();
    }
}
