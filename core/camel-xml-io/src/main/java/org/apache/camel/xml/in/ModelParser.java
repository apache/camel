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
package org.apache.camel.xml.in;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import javax.annotation.Generated;
import org.apache.camel.model.*;
import org.apache.camel.model.cloud.*;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.config.StreamResequencerConfig;
import org.apache.camel.model.dataformat.*;
import org.apache.camel.model.language.*;
import org.apache.camel.model.loadbalancer.*;
import org.apache.camel.model.rest.*;
import org.apache.camel.model.transformer.*;
import org.apache.camel.model.validator.*;
import org.apache.camel.xml.io.XmlPullParserException;

@SuppressWarnings("unused")
@Generated("org.apache.camel.xml.in.ModelParserGenerator")
public class ModelParser extends BaseParser {

    public ModelParser(
            InputStream input)
            throws IOException, XmlPullParserException {
        super(input);
    }
    public ModelParser(Reader reader) throws IOException, XmlPullParserException {
        super(reader);
    }
    public ModelParser(
            InputStream input,
            String namespace)
            throws IOException, XmlPullParserException {
        super(input, namespace);
    }
    public ModelParser(
            Reader reader,
            String namespace)
            throws IOException, XmlPullParserException {
        super(reader, namespace);
    }
    protected AggregateDefinition doParseAggregateDefinition() throws IOException, XmlPullParserException {
        return doParse(new AggregateDefinition(), (def, key, val) -> {
            switch (key) {
                case "aggregateControllerRef": def.setAggregateControllerRef(val); break;
                case "aggregationRepositoryRef": def.setAggregationRepositoryRef(val); break;
                case "closeCorrelationKeyOnCompletion": def.setCloseCorrelationKeyOnCompletion(val); break;
                case "completeAllOnStop": def.setCompleteAllOnStop(val); break;
                case "completionFromBatchConsumer": def.setCompletionFromBatchConsumer(val); break;
                case "completionInterval": def.setCompletionInterval(val); break;
                case "completionOnNewCorrelationGroup": def.setCompletionOnNewCorrelationGroup(val); break;
                case "completionSize": def.setCompletionSize(val); break;
                case "completionTimeout": def.setCompletionTimeout(val); break;
                case "completionTimeoutCheckerInterval": def.setCompletionTimeoutCheckerInterval(val); break;
                case "discardOnAggregationFailure": def.setDiscardOnAggregationFailure(val); break;
                case "discardOnCompletionTimeout": def.setDiscardOnCompletionTimeout(val); break;
                case "eagerCheckCompletion": def.setEagerCheckCompletion(val); break;
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "forceCompletionOnStop": def.setForceCompletionOnStop(val); break;
                case "ignoreInvalidCorrelationKeys": def.setIgnoreInvalidCorrelationKeys(val); break;
                case "optimisticLocking": def.setOptimisticLocking(val); break;
                case "parallelProcessing": def.setParallelProcessing(val); break;
                case "strategyMethodAllowNull": def.setStrategyMethodAllowNull(val); break;
                case "strategyMethodName": def.setStrategyMethodName(val); break;
                case "strategyRef": def.setStrategyRef(val); break;
                case "timeoutCheckerExecutorServiceRef": def.setTimeoutCheckerExecutorServiceRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "completionPredicate": def.setCompletionPredicate(doParseExpressionSubElementDefinition()); break;
                case "completionSizeExpression": def.setCompletionSizeExpression(doParseExpressionSubElementDefinition()); break;
                case "completionTimeoutExpression": def.setCompletionTimeoutExpression(doParseExpressionSubElementDefinition()); break;
                case "correlationExpression": def.setCorrelationExpression(doParseExpressionSubElementDefinition()); break;
                case "optimisticLockRetryPolicy": def.setOptimisticLockRetryPolicyDefinition(doParseOptimisticLockRetryPolicyDefinition()); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ExpressionSubElementDefinition doParseExpressionSubElementDefinition() throws IOException, XmlPullParserException {
        return doParse(new ExpressionSubElementDefinition(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "expressionDefinition": def.setExpressionType(doParseExpressionDefinition()); break;
                case "constant": def.setExpressionType(doParseConstantExpression()); break;
                case "exchangeProperty": def.setExpressionType(doParseExchangePropertyExpression()); break;
                case "groovy": def.setExpressionType(doParseGroovyExpression()); break;
                case "header": def.setExpressionType(doParseHeaderExpression()); break;
                case "hl7terser": def.setExpressionType(doParseHl7TerserExpression()); break;
                case "jsonpath": def.setExpressionType(doParseJsonPathExpression()); break;
                case "language": def.setExpressionType(doParseLanguageExpression()); break;
                case "method": def.setExpressionType(doParseMethodCallExpression()); break;
                case "mvel": def.setExpressionType(doParseMvelExpression()); break;
                case "ognl": def.setExpressionType(doParseOgnlExpression()); break;
                case "ref": def.setExpressionType(doParseRefExpression()); break;
                case "simple": def.setExpressionType(doParseSimpleExpression()); break;
                case "spel": def.setExpressionType(doParseSpELExpression()); break;
                case "tokenize": def.setExpressionType(doParseTokenizerExpression()); break;
                case "xtokenize": def.setExpressionType(doParseXMLTokenizerExpression()); break;
                case "xpath": def.setExpressionType(doParseXPathExpression()); break;
                case "xquery": def.setExpressionType(doParseXQueryExpression()); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected OptimisticLockRetryPolicyDefinition doParseOptimisticLockRetryPolicyDefinition() throws IOException, XmlPullParserException {
        return doParse(new OptimisticLockRetryPolicyDefinition(), (def, key, val) -> {
            switch (key) {
                case "exponentialBackOff": def.setExponentialBackOff(val); break;
                case "maximumRetries": def.setMaximumRetries(val); break;
                case "maximumRetryDelay": def.setMaximumRetryDelay(val); break;
                case "randomBackOff": def.setRandomBackOff(val); break;
                case "retryDelay": def.setRetryDelay(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected BeanDefinition doParseBeanDefinition() throws IOException, XmlPullParserException {
        return doParse(new BeanDefinition(), (def, key, val) -> {
            switch (key) {
                case "beanType": def.setBeanType(val); break;
                case "cache": def.setCache(val); break;
                case "method": def.setMethod(val); break;
                case "ref": def.setRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected CatchDefinition doParseCatchDefinition() throws IOException, XmlPullParserException {
        return doParse(new CatchDefinition(),
            processorDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "exception": doAdd(doParseText(), def.getExceptions(), def::setExceptions); break;
                case "onWhen": def.setOnWhen(doParseWhenDefinition()); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected WhenDefinition doParseWhenDefinition() throws IOException, XmlPullParserException {
        return doParse(new WhenDefinition(),
            processorDefinitionAttributeHandler(), outputExpressionNodeElementHandler(), noValueHandler());
    }
    protected ChoiceDefinition doParseChoiceDefinition() throws IOException, XmlPullParserException {
        return doParse(new ChoiceDefinition(),
            processorDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "otherwise": def.setOtherwise(doParseOtherwiseDefinition()); break;
                case "when": doAdd(doParseWhenDefinition(), def.getWhenClauses(), def::setWhenClauses); break;
                case "whenSkipSendToEndpoint": doAdd(doParseWhenSkipSendToEndpointDefinition(), def.getWhenClauses(), def::setWhenClauses); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected OtherwiseDefinition doParseOtherwiseDefinition() throws IOException, XmlPullParserException {
        return doParse(new OtherwiseDefinition(),
            processorDefinitionAttributeHandler(), outputDefinitionElementHandler(), noValueHandler());
    }
    protected CircuitBreakerDefinition doParseCircuitBreakerDefinition() throws IOException, XmlPullParserException {
        return doParse(new CircuitBreakerDefinition(), (def, key, val) -> {
            switch (key) {
                case "configurationRef": def.setConfigurationRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "hystrixConfiguration": def.setHystrixConfiguration(doParseHystrixConfigurationDefinition()); break;
                case "resilience4jConfiguration": def.setResilience4jConfiguration(doParseResilience4jConfigurationDefinition()); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected HystrixConfigurationDefinition doParseHystrixConfigurationDefinition() throws IOException, XmlPullParserException {
        return doParse(new HystrixConfigurationDefinition(), (def, key, val) -> {
            switch (key) {
                case "allowMaximumSizeToDivergeFromCoreSize": def.setAllowMaximumSizeToDivergeFromCoreSize(val); break;
                case "circuitBreakerEnabled": def.setCircuitBreakerEnabled(val); break;
                case "circuitBreakerErrorThresholdPercentage": def.setCircuitBreakerErrorThresholdPercentage(val); break;
                case "circuitBreakerForceClosed": def.setCircuitBreakerForceClosed(val); break;
                case "circuitBreakerForceOpen": def.setCircuitBreakerForceOpen(val); break;
                case "circuitBreakerRequestVolumeThreshold": def.setCircuitBreakerRequestVolumeThreshold(val); break;
                case "circuitBreakerSleepWindowInMilliseconds": def.setCircuitBreakerSleepWindowInMilliseconds(val); break;
                case "corePoolSize": def.setCorePoolSize(val); break;
                case "executionIsolationSemaphoreMaxConcurrentRequests": def.setExecutionIsolationSemaphoreMaxConcurrentRequests(val); break;
                case "executionIsolationStrategy": def.setExecutionIsolationStrategy(val); break;
                case "executionIsolationThreadInterruptOnTimeout": def.setExecutionIsolationThreadInterruptOnTimeout(val); break;
                case "executionTimeoutEnabled": def.setExecutionTimeoutEnabled(val); break;
                case "executionTimeoutInMilliseconds": def.setExecutionTimeoutInMilliseconds(val); break;
                case "fallbackEnabled": def.setFallbackEnabled(val); break;
                case "fallbackIsolationSemaphoreMaxConcurrentRequests": def.setFallbackIsolationSemaphoreMaxConcurrentRequests(val); break;
                case "groupKey": def.setGroupKey(val); break;
                case "keepAliveTime": def.setKeepAliveTime(val); break;
                case "maxQueueSize": def.setMaxQueueSize(val); break;
                case "maximumSize": def.setMaximumSize(val); break;
                case "metricsHealthSnapshotIntervalInMilliseconds": def.setMetricsHealthSnapshotIntervalInMilliseconds(val); break;
                case "metricsRollingPercentileBucketSize": def.setMetricsRollingPercentileBucketSize(val); break;
                case "metricsRollingPercentileEnabled": def.setMetricsRollingPercentileEnabled(val); break;
                case "metricsRollingPercentileWindowBuckets": def.setMetricsRollingPercentileWindowBuckets(val); break;
                case "metricsRollingPercentileWindowInMilliseconds": def.setMetricsRollingPercentileWindowInMilliseconds(val); break;
                case "metricsRollingStatisticalWindowBuckets": def.setMetricsRollingStatisticalWindowBuckets(val); break;
                case "metricsRollingStatisticalWindowInMilliseconds": def.setMetricsRollingStatisticalWindowInMilliseconds(val); break;
                case "queueSizeRejectionThreshold": def.setQueueSizeRejectionThreshold(val); break;
                case "requestLogEnabled": def.setRequestLogEnabled(val); break;
                case "threadPoolKey": def.setThreadPoolKey(val); break;
                case "threadPoolRollingNumberStatisticalWindowBuckets": def.setThreadPoolRollingNumberStatisticalWindowBuckets(val); break;
                case "threadPoolRollingNumberStatisticalWindowInMilliseconds": def.setThreadPoolRollingNumberStatisticalWindowInMilliseconds(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected Resilience4jConfigurationDefinition doParseResilience4jConfigurationDefinition() throws IOException, XmlPullParserException {
        return doParse(new Resilience4jConfigurationDefinition(), (def, key, val) -> {
            switch (key) {
                case "automaticTransitionFromOpenToHalfOpenEnabled": def.setAutomaticTransitionFromOpenToHalfOpenEnabled(java.lang.Boolean.valueOf(val)); break;
                case "circuitBreakerRef": def.setCircuitBreakerRef(val); break;
                case "configRef": def.setConfigRef(val); break;
                case "failureRateThreshold": def.setFailureRateThreshold(java.lang.Float.valueOf(val)); break;
                case "minimumNumberOfCalls": def.setMinimumNumberOfCalls(java.lang.Integer.valueOf(val)); break;
                case "permittedNumberOfCallsInHalfOpenState": def.setPermittedNumberOfCallsInHalfOpenState(java.lang.Integer.valueOf(val)); break;
                case "slidingWindowSize": def.setSlidingWindowSize(java.lang.Integer.valueOf(val)); break;
                case "slidingWindowType": def.setSlidingWindowType(val); break;
                case "slowCallDurationThreshold": def.setSlowCallDurationThreshold(java.lang.Integer.valueOf(val)); break;
                case "slowCallRateThreshold": def.setSlowCallRateThreshold(java.lang.Float.valueOf(val)); break;
                case "waitDurationInOpenState": def.setWaitDurationInOpenState(java.lang.Integer.valueOf(val)); break;
                case "writableStackTraceEnabled": def.setWritableStackTraceEnabled(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "bulkheadEnabled": def.setBulkheadEnabled(Boolean.valueOf(doParseText())); break;
                case "bulkheadMaxConcurrentCalls": def.setBulkheadMaxConcurrentCalls(Integer.valueOf(doParseText())); break;
                case "bulkheadMaxWaitDuration": def.setBulkheadMaxWaitDuration(Integer.valueOf(doParseText())); break;
                case "timeoutCancelRunningFuture": def.setTimeoutCancelRunningFuture(Boolean.valueOf(doParseText())); break;
                case "timeoutDuration": def.setTimeoutDuration(Integer.valueOf(doParseText())); break;
                case "timeoutEnabled": def.setTimeoutEnabled(Boolean.valueOf(doParseText())); break;
                case "timeoutExecutorServiceRef": def.setTimeoutExecutorServiceRef(doParseText()); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected ClaimCheckDefinition doParseClaimCheckDefinition() throws IOException, XmlPullParserException {
        return doParse(new ClaimCheckDefinition(), (def, key, val) -> {
            switch (key) {
                case "strategyMethodName": def.setAggregationStrategyMethodName(val); break;
                case "strategyRef": def.setAggregationStrategyRef(val); break;
                case "filter": def.setFilter(val); break;
                case "key": def.setKey(val); break;
                case "operation": def.setOperation(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ContextScanDefinition doParseContextScanDefinition() throws IOException, XmlPullParserException {
        return doParse(new ContextScanDefinition(), (def, key, val) -> {
            switch (key) {
                case "includeNonSingletons": def.setIncludeNonSingletons(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "excludes": doAdd(doParseText(), def.getExcludes(), def::setExcludes); break;
                case "includes": doAdd(doParseText(), def.getIncludes(), def::setIncludes); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected ConvertBodyDefinition doParseConvertBodyDefinition() throws IOException, XmlPullParserException {
        return doParse(new ConvertBodyDefinition(), (def, key, val) -> {
            switch (key) {
                case "charset": def.setCharset(val); break;
                case "type": def.setType(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected DataFormatDefinition doParseDataFormatDefinition() throws IOException, XmlPullParserException {
        return doParse(new DataFormatDefinition(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected DelayDefinition doParseDelayDefinition() throws IOException, XmlPullParserException {
        return doParse(new DelayDefinition(), (def, key, val) -> {
            switch (key) {
                case "asyncDelayed": def.setAsyncDelayed(val); break;
                case "callerRunsWhenRejected": def.setCallerRunsWhenRejected(val); break;
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected DescriptionDefinition doParseDescriptionDefinition() throws IOException, XmlPullParserException {
        return doParse(new DescriptionDefinition(), (def, key, val) -> {
            switch (key) {
                case "lang": def.setLang(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), (def, val) -> def.setText(val));
    }
    protected DynamicRouterDefinition doParseDynamicRouterDefinition() throws IOException, XmlPullParserException {
        return doParse(new DynamicRouterDefinition(), (def, key, val) -> {
            switch (key) {
                case "cacheSize": def.setCacheSize(val); break;
                case "ignoreInvalidEndpoints": def.setIgnoreInvalidEndpoints(val); break;
                case "uriDelimiter": def.setUriDelimiter(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected EnrichDefinition doParseEnrichDefinition() throws IOException, XmlPullParserException {
        return doParse(new EnrichDefinition(), (def, key, val) -> {
            switch (key) {
                case "aggregateOnException": def.setAggregateOnException(val); break;
                case "strategyMethodAllowNull": def.setAggregationStrategyMethodAllowNull(val); break;
                case "strategyMethodName": def.setAggregationStrategyMethodName(val); break;
                case "strategyRef": def.setAggregationStrategyRef(val); break;
                case "cacheSize": def.setCacheSize(val); break;
                case "ignoreInvalidEndpoint": def.setIgnoreInvalidEndpoint(val); break;
                case "shareUnitOfWork": def.setShareUnitOfWork(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected ExpressionDefinition doParseExpressionDefinition() throws IOException, XmlPullParserException {
        return doParse(new ExpressionDefinition(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected FilterDefinition doParseFilterDefinition() throws IOException, XmlPullParserException {
        return doParse(new FilterDefinition(),
            processorDefinitionAttributeHandler(), outputExpressionNodeElementHandler(), noValueHandler());
    }
    protected FinallyDefinition doParseFinallyDefinition() throws IOException, XmlPullParserException {
        return doParse(new FinallyDefinition(),
            processorDefinitionAttributeHandler(), outputDefinitionElementHandler(), noValueHandler());
    }
    protected FromDefinition doParseFromDefinition() throws IOException, XmlPullParserException {
        return doParse(new FromDefinition(), (def, key, val) -> {
            switch (key) {
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected GlobalOptionDefinition doParseGlobalOptionDefinition() throws IOException, XmlPullParserException {
        return doParse(new GlobalOptionDefinition(), (def, key, val) -> {
            switch (key) {
                case "key": def.setKey(val); break;
                case "value": def.setValue(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected GlobalOptionsDefinition doParseGlobalOptionsDefinition() throws IOException, XmlPullParserException {
        return doParse(new GlobalOptionsDefinition(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "globalOption": doAdd(doParseGlobalOptionDefinition(), def.getGlobalOptions(), def::setGlobalOptions); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected IdempotentConsumerDefinition doParseIdempotentConsumerDefinition() throws IOException, XmlPullParserException {
        return doParse(new IdempotentConsumerDefinition(), (def, key, val) -> {
            switch (key) {
                case "completionEager": def.setCompletionEager(val); break;
                case "eager": def.setEager(val); break;
                case "messageIdRepositoryRef": def.setMessageIdRepositoryRef(val); break;
                case "removeOnFailure": def.setRemoveOnFailure(val); break;
                case "skipDuplicate": def.setSkipDuplicate(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputExpressionNodeElementHandler(), noValueHandler());
    }
    protected InOnlyDefinition doParseInOnlyDefinition() throws IOException, XmlPullParserException {
        return doParse(new InOnlyDefinition(), (def, key, val) -> {
            switch (key) {
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected InOutDefinition doParseInOutDefinition() throws IOException, XmlPullParserException {
        return doParse(new InOutDefinition(), (def, key, val) -> {
            switch (key) {
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected InputTypeDefinition doParseInputTypeDefinition() throws IOException, XmlPullParserException {
        return doParse(new InputTypeDefinition(), (def, key, val) -> {
            switch (key) {
                case "urn": def.setUrn(val); break;
                case "validate": def.setValidate(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected InterceptDefinition doParseInterceptDefinition() throws IOException, XmlPullParserException {
        return doParse(new InterceptDefinition(),
            processorDefinitionAttributeHandler(), outputDefinitionElementHandler(), noValueHandler());
    }
    protected InterceptFromDefinition doParseInterceptFromDefinition() throws IOException, XmlPullParserException {
        return doParse(new InterceptFromDefinition(), (def, key, val) -> {
            switch (key) {
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputDefinitionElementHandler(), noValueHandler());
    }
    protected InterceptSendToEndpointDefinition doParseInterceptSendToEndpointDefinition() throws IOException, XmlPullParserException {
        return doParse(new InterceptSendToEndpointDefinition(), (def, key, val) -> {
            switch (key) {
                case "afterUri": def.setAfterUri(val); break;
                case "skipSendToOriginalEndpoint": def.setSkipSendToOriginalEndpoint(val); break;
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputDefinitionElementHandler(), noValueHandler());
    }
    protected LoadBalanceDefinition doParseLoadBalanceDefinition() throws IOException, XmlPullParserException {
        return doParse(new LoadBalanceDefinition(),
            processorDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "failover": def.setLoadBalancerType(doParseFailoverLoadBalancerDefinition()); break;
                case "random": def.setLoadBalancerType(doParseRandomLoadBalancerDefinition()); break;
                case "customLoadBalancer": def.setLoadBalancerType(doParseCustomLoadBalancerDefinition()); break;
                case "roundRobin": def.setLoadBalancerType(doParseRoundRobinLoadBalancerDefinition()); break;
                case "sticky": def.setLoadBalancerType(doParseStickyLoadBalancerDefinition()); break;
                case "topic": def.setLoadBalancerType(doParseTopicLoadBalancerDefinition()); break;
                case "weighted": def.setLoadBalancerType(doParseWeightedLoadBalancerDefinition()); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected LoadBalancerDefinition doParseLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new LoadBalancerDefinition(),
            identifiedTypeAttributeHandler(), emptyElementHandler(), noValueHandler());
    }
    protected LogDefinition doParseLogDefinition() throws IOException, XmlPullParserException {
        return doParse(new LogDefinition(), (def, key, val) -> {
            switch (key) {
                case "logName": def.setLogName(val); break;
                case "loggerRef": def.setLoggerRef(val); break;
                case "loggingLevel": def.setLoggingLevel(val); break;
                case "marker": def.setMarker(val); break;
                case "message": def.setMessage(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected LoopDefinition doParseLoopDefinition() throws IOException, XmlPullParserException {
        return doParse(new LoopDefinition(), (def, key, val) -> {
            switch (key) {
                case "copy": def.setCopy(val); break;
                case "doWhile": def.setDoWhile(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputExpressionNodeElementHandler(), noValueHandler());
    }
    protected MarshalDefinition doParseMarshalDefinition() throws IOException, XmlPullParserException {
        return doParse(new MarshalDefinition(),
            processorDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "any23": def.setDataFormatType(doParseAny23DataFormat()); break;
                case "asn1": def.setDataFormatType(doParseASN1DataFormat()); break;
                case "avro": def.setDataFormatType(doParseAvroDataFormat()); break;
                case "barcode": def.setDataFormatType(doParseBarcodeDataFormat()); break;
                case "base64": def.setDataFormatType(doParseBase64DataFormat()); break;
                case "beanio": def.setDataFormatType(doParseBeanioDataFormat()); break;
                case "bindy": def.setDataFormatType(doParseBindyDataFormat()); break;
                case "cbor": def.setDataFormatType(doParseCBORDataFormat()); break;
                case "crypto": def.setDataFormatType(doParseCryptoDataFormat()); break;
                case "csv": def.setDataFormatType(doParseCsvDataFormat()); break;
                case "custom": def.setDataFormatType(doParseCustomDataFormat()); break;
                case "fhirJson": def.setDataFormatType(doParseFhirJsonDataFormat()); break;
                case "fhirXml": def.setDataFormatType(doParseFhirXmlDataFormat()); break;
                case "flatpack": def.setDataFormatType(doParseFlatpackDataFormat()); break;
                case "grok": def.setDataFormatType(doParseGrokDataFormat()); break;
                case "gzip": def.setDataFormatType(doParseGzipDataFormat()); break;
                case "hl7": def.setDataFormatType(doParseHL7DataFormat()); break;
                case "ical": def.setDataFormatType(doParseIcalDataFormat()); break;
                case "jacksonxml": def.setDataFormatType(doParseJacksonXMLDataFormat()); break;
                case "jaxb": def.setDataFormatType(doParseJaxbDataFormat()); break;
                case "json": def.setDataFormatType(doParseJsonDataFormat()); break;
                case "jsonApi": def.setDataFormatType(doParseJsonApiDataFormat()); break;
                case "lzf": def.setDataFormatType(doParseLZFDataFormat()); break;
                case "mimeMultipart": def.setDataFormatType(doParseMimeMultipartDataFormat()); break;
                case "protobuf": def.setDataFormatType(doParseProtobufDataFormat()); break;
                case "rss": def.setDataFormatType(doParseRssDataFormat()); break;
                case "secureXML": def.setDataFormatType(doParseXMLSecurityDataFormat()); break;
                case "soapjaxb": def.setDataFormatType(doParseSoapJaxbDataFormat()); break;
                case "syslog": def.setDataFormatType(doParseSyslogDataFormat()); break;
                case "tarfile": def.setDataFormatType(doParseTarFileDataFormat()); break;
                case "thrift": def.setDataFormatType(doParseThriftDataFormat()); break;
                case "tidyMarkup": def.setDataFormatType(doParseTidyMarkupDataFormat()); break;
                case "univocity-csv": def.setDataFormatType(doParseUniVocityCsvDataFormat()); break;
                case "univocity-fixed": def.setDataFormatType(doParseUniVocityFixedWidthDataFormat()); break;
                case "univocity-tsv": def.setDataFormatType(doParseUniVocityTsvDataFormat()); break;
                case "xmlrpc": def.setDataFormatType(doParseXmlRpcDataFormat()); break;
                case "xstream": def.setDataFormatType(doParseXStreamDataFormat()); break;
                case "pgp": def.setDataFormatType(doParsePGPDataFormat()); break;
                case "yaml": def.setDataFormatType(doParseYAMLDataFormat()); break;
                case "zip": def.setDataFormatType(doParseZipDeflaterDataFormat()); break;
                case "zipfile": def.setDataFormatType(doParseZipFileDataFormat()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected MulticastDefinition doParseMulticastDefinition() throws IOException, XmlPullParserException {
        return doParse(new MulticastDefinition(), (def, key, val) -> {
            switch (key) {
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "onPrepareRef": def.setOnPrepareRef(val); break;
                case "parallelAggregate": def.setParallelAggregate(val); break;
                case "parallelProcessing": def.setParallelProcessing(val); break;
                case "shareUnitOfWork": def.setShareUnitOfWork(val); break;
                case "stopOnAggregateException": def.setStopOnAggregateException(val); break;
                case "stopOnException": def.setStopOnException(val); break;
                case "strategyMethodAllowNull": def.setStrategyMethodAllowNull(val); break;
                case "strategyMethodName": def.setStrategyMethodName(val); break;
                case "strategyRef": def.setStrategyRef(val); break;
                case "streaming": def.setStreaming(val); break;
                case "timeout": def.setTimeout(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputDefinitionElementHandler(), noValueHandler());
    }
    protected OnCompletionDefinition doParseOnCompletionDefinition() throws IOException, XmlPullParserException {
        return doParse(new OnCompletionDefinition(), (def, key, val) -> {
            switch (key) {
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "mode": def.setMode(org.apache.camel.model.OnCompletionMode.valueOf(val)); break;
                case "onCompleteOnly": def.setOnCompleteOnly(val); break;
                case "onFailureOnly": def.setOnFailureOnly(val); break;
                case "parallelProcessing": def.setParallelProcessing(val); break;
                case "useOriginalMessage": def.setUseOriginalMessage(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "onWhen": def.setOnWhen(doParseWhenDefinition()); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected OnExceptionDefinition doParseOnExceptionDefinition() throws IOException, XmlPullParserException {
        return doParse(new OnExceptionDefinition(), (def, key, val) -> {
            switch (key) {
                case "onExceptionOccurredRef": def.setOnExceptionOccurredRef(val); break;
                case "onRedeliveryRef": def.setOnRedeliveryRef(val); break;
                case "redeliveryPolicyRef": def.setRedeliveryPolicyRef(val); break;
                case "useOriginalBody": def.setUseOriginalBody(val); break;
                case "useOriginalMessage": def.setUseOriginalMessage(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "continued": def.setContinued(doParseExpressionSubElementDefinition()); break;
                case "exception": doAdd(doParseText(), def.getExceptions(), def::setExceptions); break;
                case "handled": def.setHandled(doParseExpressionSubElementDefinition()); break;
                case "onWhen": def.setOnWhen(doParseWhenDefinition()); break;
                case "redeliveryPolicy": def.setRedeliveryPolicyType(doParseRedeliveryPolicyDefinition()); break;
                case "retryWhile": def.setRetryWhile(doParseExpressionSubElementDefinition()); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected RedeliveryPolicyDefinition doParseRedeliveryPolicyDefinition() throws IOException, XmlPullParserException {
        return doParse(new RedeliveryPolicyDefinition(), (def, key, val) -> {
            switch (key) {
                case "allowRedeliveryWhileStopping": def.setAllowRedeliveryWhileStopping(val); break;
                case "asyncDelayedRedelivery": def.setAsyncDelayedRedelivery(val); break;
                case "backOffMultiplier": def.setBackOffMultiplier(val); break;
                case "collisionAvoidanceFactor": def.setCollisionAvoidanceFactor(val); break;
                case "delayPattern": def.setDelayPattern(val); break;
                case "disableRedelivery": def.setDisableRedelivery(val); break;
                case "exchangeFormatterRef": def.setExchangeFormatterRef(val); break;
                case "logContinued": def.setLogContinued(val); break;
                case "logExhausted": def.setLogExhausted(val); break;
                case "logExhaustedMessageBody": def.setLogExhaustedMessageBody(val); break;
                case "logExhaustedMessageHistory": def.setLogExhaustedMessageHistory(val); break;
                case "logHandled": def.setLogHandled(val); break;
                case "logNewException": def.setLogNewException(val); break;
                case "logRetryAttempted": def.setLogRetryAttempted(val); break;
                case "logRetryStackTrace": def.setLogRetryStackTrace(val); break;
                case "logStackTrace": def.setLogStackTrace(val); break;
                case "maximumRedeliveries": def.setMaximumRedeliveries(val); break;
                case "maximumRedeliveryDelay": def.setMaximumRedeliveryDelay(val); break;
                case "redeliveryDelay": def.setRedeliveryDelay(val); break;
                case "retriesExhaustedLogLevel": def.setRetriesExhaustedLogLevel(val); break;
                case "retryAttemptedLogInterval": def.setRetryAttemptedLogInterval(val); break;
                case "retryAttemptedLogLevel": def.setRetryAttemptedLogLevel(val); break;
                case "useCollisionAvoidance": def.setUseCollisionAvoidance(val); break;
                case "useExponentialBackOff": def.setUseExponentialBackOff(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected OnFallbackDefinition doParseOnFallbackDefinition() throws IOException, XmlPullParserException {
        return doParse(new OnFallbackDefinition(), (def, key, val) -> {
            switch (key) {
                case "fallbackViaNetwork": def.setFallbackViaNetwork(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputDefinitionElementHandler(), noValueHandler());
    }
    protected OutputDefinition doParseOutputDefinition() throws IOException, XmlPullParserException {
        return doParse(new OutputDefinition(),
            processorDefinitionAttributeHandler(), outputDefinitionElementHandler(), noValueHandler());
    }
    protected OutputTypeDefinition doParseOutputTypeDefinition() throws IOException, XmlPullParserException {
        return doParse(new OutputTypeDefinition(), (def, key, val) -> {
            switch (key) {
                case "urn": def.setUrn(val); break;
                case "validate": def.setValidate(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected PackageScanDefinition doParsePackageScanDefinition() throws IOException, XmlPullParserException {
        return doParse(new PackageScanDefinition(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "excludes": doAdd(doParseText(), def.getExcludes(), def::setExcludes); break;
                case "includes": doAdd(doParseText(), def.getIncludes(), def::setIncludes); break;
                case "package": doAdd(doParseText(), def.getPackages(), def::setPackages); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected PipelineDefinition doParsePipelineDefinition() throws IOException, XmlPullParserException {
        return doParse(new PipelineDefinition(),
            processorDefinitionAttributeHandler(), outputDefinitionElementHandler(), noValueHandler());
    }
    protected PolicyDefinition doParsePolicyDefinition() throws IOException, XmlPullParserException {
        return doParse(new PolicyDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputDefinitionElementHandler(), noValueHandler());
    }
    protected PollEnrichDefinition doParsePollEnrichDefinition() throws IOException, XmlPullParserException {
        return doParse(new PollEnrichDefinition(), (def, key, val) -> {
            switch (key) {
                case "aggregateOnException": def.setAggregateOnException(val); break;
                case "strategyMethodAllowNull": def.setAggregationStrategyMethodAllowNull(val); break;
                case "strategyMethodName": def.setAggregationStrategyMethodName(val); break;
                case "strategyRef": def.setAggregationStrategyRef(val); break;
                case "cacheSize": def.setCacheSize(val); break;
                case "ignoreInvalidEndpoint": def.setIgnoreInvalidEndpoint(val); break;
                case "timeout": def.setTimeout(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected ProcessDefinition doParseProcessDefinition() throws IOException, XmlPullParserException {
        return doParse(new ProcessDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected PropertyDefinition doParsePropertyDefinition() throws IOException, XmlPullParserException {
        return doParse(new PropertyDefinition(), (def, key, val) -> {
            switch (key) {
                case "key": def.setKey(val); break;
                case "value": def.setValue(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected PropertyDefinitions doParsePropertyDefinitions() throws IOException, XmlPullParserException {
        return doParse(new PropertyDefinitions(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "properties": doAdd(doParsePropertyDefinition(), def.getProperties(), def::setProperties); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected RecipientListDefinition doParseRecipientListDefinition() throws IOException, XmlPullParserException {
        return doParse(new RecipientListDefinition(), (def, key, val) -> {
            switch (key) {
                case "cacheSize": def.setCacheSize(val); break;
                case "delimiter": def.setDelimiter(val); break;
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "ignoreInvalidEndpoints": def.setIgnoreInvalidEndpoints(val); break;
                case "onPrepareRef": def.setOnPrepareRef(val); break;
                case "parallelAggregate": def.setParallelAggregate(val); break;
                case "parallelProcessing": def.setParallelProcessing(val); break;
                case "shareUnitOfWork": def.setShareUnitOfWork(val); break;
                case "stopOnAggregateException": def.setStopOnAggregateException(val); break;
                case "stopOnException": def.setStopOnException(val); break;
                case "strategyMethodAllowNull": def.setStrategyMethodAllowNull(val); break;
                case "strategyMethodName": def.setStrategyMethodName(val); break;
                case "strategyRef": def.setStrategyRef(val); break;
                case "streaming": def.setStreaming(val); break;
                case "timeout": def.setTimeout(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected RemoveHeaderDefinition doParseRemoveHeaderDefinition() throws IOException, XmlPullParserException {
        return doParse(new RemoveHeaderDefinition(), (def, key, val) -> {
            switch (key) {
                case "headerName": def.setHeaderName(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected RemoveHeadersDefinition doParseRemoveHeadersDefinition() throws IOException, XmlPullParserException {
        return doParse(new RemoveHeadersDefinition(), (def, key, val) -> {
            switch (key) {
                case "excludePattern": def.setExcludePattern(val); break;
                case "pattern": def.setPattern(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected RemovePropertiesDefinition doParseRemovePropertiesDefinition() throws IOException, XmlPullParserException {
        return doParse(new RemovePropertiesDefinition(), (def, key, val) -> {
            switch (key) {
                case "excludePattern": def.setExcludePattern(val); break;
                case "pattern": def.setPattern(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected RemovePropertyDefinition doParseRemovePropertyDefinition() throws IOException, XmlPullParserException {
        return doParse(new RemovePropertyDefinition(), (def, key, val) -> {
            switch (key) {
                case "propertyName": def.setPropertyName(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ResequenceDefinition doParseResequenceDefinition() throws IOException, XmlPullParserException {
        return doParse(new ResequenceDefinition(),
            processorDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "expressionDefinition": def.setExpression(doParseExpressionDefinition()); break;
                case "constant": def.setExpression(doParseConstantExpression()); break;
                case "exchangeProperty": def.setExpression(doParseExchangePropertyExpression()); break;
                case "groovy": def.setExpression(doParseGroovyExpression()); break;
                case "header": def.setExpression(doParseHeaderExpression()); break;
                case "hl7terser": def.setExpression(doParseHl7TerserExpression()); break;
                case "jsonpath": def.setExpression(doParseJsonPathExpression()); break;
                case "language": def.setExpression(doParseLanguageExpression()); break;
                case "method": def.setExpression(doParseMethodCallExpression()); break;
                case "mvel": def.setExpression(doParseMvelExpression()); break;
                case "ognl": def.setExpression(doParseOgnlExpression()); break;
                case "ref": def.setExpression(doParseRefExpression()); break;
                case "simple": def.setExpression(doParseSimpleExpression()); break;
                case "spel": def.setExpression(doParseSpELExpression()); break;
                case "tokenize": def.setExpression(doParseTokenizerExpression()); break;
                case "xtokenize": def.setExpression(doParseXMLTokenizerExpression()); break;
                case "xpath": def.setExpression(doParseXPathExpression()); break;
                case "xquery": def.setExpression(doParseXQueryExpression()); break;
                case "batch-config": def.setResequencerConfig(doParseBatchResequencerConfig()); break;
                case "stream-config": def.setResequencerConfig(doParseStreamResequencerConfig()); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected RestContextRefDefinition doParseRestContextRefDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestContextRefDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected RollbackDefinition doParseRollbackDefinition() throws IOException, XmlPullParserException {
        return doParse(new RollbackDefinition(), (def, key, val) -> {
            switch (key) {
                case "markRollbackOnly": def.setMarkRollbackOnly(val); break;
                case "markRollbackOnlyLast": def.setMarkRollbackOnlyLast(val); break;
                case "message": def.setMessage(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected RouteBuilderDefinition doParseRouteBuilderDefinition() throws IOException, XmlPullParserException {
        return doParse(new RouteBuilderDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected RouteContextRefDefinition doParseRouteContextRefDefinition() throws IOException, XmlPullParserException {
        return doParse(new RouteContextRefDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected RouteDefinition doParseRouteDefinition() throws IOException, XmlPullParserException {
        return doParse(new RouteDefinition(), (def, key, val) -> {
            switch (key) {
                case "autoStartup": def.setAutoStartup(val); break;
                case "delayer": def.setDelayer(val); break;
                case "errorHandlerRef": def.setErrorHandlerRef(val); break;
                case "group": def.setGroup(val); break;
                case "logMask": def.setLogMask(val); break;
                case "messageHistory": def.setMessageHistory(val); break;
                case "routePolicyRef": def.setRoutePolicyRef(val); break;
                case "shutdownRoute": def.setShutdownRoute(org.apache.camel.ShutdownRoute.valueOf(val)); break;
                case "shutdownRunningTask": def.setShutdownRunningTask(org.apache.camel.ShutdownRunningTask.valueOf(val)); break;
                case "startupOrder": def.setStartupOrder(java.lang.Integer.valueOf(val)); break;
                case "streamCache": def.setStreamCache(val); break;
                case "trace": def.setTrace(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "from": def.setInput(doParseFromDefinition()); break;
                case "inputType": def.setInputType(doParseInputTypeDefinition()); break;
                case "outputType": def.setOutputType(doParseOutputTypeDefinition()); break;
                case "routeProperty": doAdd(doParsePropertyDefinition(), def.getRouteProperties(), def::setRouteProperties); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected RestDefinition doParseRestDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "path": def.setPath(val); break;
                case "produces": def.setProduces(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "tag": def.setTag(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "securityDefinitions": def.setSecurityDefinitions(doParseRestSecuritiesDefinition()); break;
                case "delete": doAdd(doParseDeleteVerbDefinition(), def.getVerbs(), def::setVerbs); break;
                case "get": doAdd(doParseGetVerbDefinition(), def.getVerbs(), def::setVerbs); break;
                case "head": doAdd(doParseHeadVerbDefinition(), def.getVerbs(), def::setVerbs); break;
                case "patch": doAdd(doParsePatchVerbDefinition(), def.getVerbs(), def::setVerbs); break;
                case "post": doAdd(doParsePostVerbDefinition(), def.getVerbs(), def::setVerbs); break;
                case "put": doAdd(doParsePutVerbDefinition(), def.getVerbs(), def::setVerbs); break;
                case "verb": doAdd(doParseVerbDefinition(), def.getVerbs(), def::setVerbs); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected RestBindingDefinition doParseRestBindingDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestBindingDefinition(), (def, key, val) -> {
            switch (key) {
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "component": def.setComponent(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    public RoutesDefinition parseRoutesDefinition()
            throws IOException, XmlPullParserException {
        expectTag("routes");
        return doParseRoutesDefinition();
    }
    protected RoutesDefinition doParseRoutesDefinition() throws IOException, XmlPullParserException {
        return doParse(new RoutesDefinition(),
            optionalIdentifiedDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "route": doAdd(doParseRouteDefinition(), def.getRoutes(), def::setRoutes); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected RoutingSlipDefinition doParseRoutingSlipDefinition() throws IOException, XmlPullParserException {
        return doParse(new RoutingSlipDefinition(), (def, key, val) -> {
            switch (key) {
                case "cacheSize": def.setCacheSize(val); break;
                case "ignoreInvalidEndpoints": def.setIgnoreInvalidEndpoints(val); break;
                case "uriDelimiter": def.setUriDelimiter(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected SagaDefinition doParseSagaDefinition() throws IOException, XmlPullParserException {
        return doParse(new SagaDefinition(), (def, key, val) -> {
            switch (key) {
                case "completionMode": def.setCompletionMode(org.apache.camel.model.SagaCompletionMode.valueOf(val)); break;
                case "propagation": def.setPropagation(org.apache.camel.model.SagaPropagation.valueOf(val)); break;
                case "sagaServiceRef": def.setSagaServiceRef(val); break;
                case "timeoutInMilliseconds": def.setTimeoutInMilliseconds(java.lang.Long.valueOf(val)); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "compensation": def.setCompensation(doParseSagaActionUriDefinition()); break;
                case "completion": def.setCompletion(doParseSagaActionUriDefinition()); break;
                case "option": doAdd(doParseSagaOptionDefinition(), def.getOptions(), def::setOptions); break;
                default: return outputDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected SagaActionUriDefinition doParseSagaActionUriDefinition() throws IOException, XmlPullParserException {
        return doParse(new SagaActionUriDefinition(), (def, key, val) -> {
            switch (key) {
                case "uri": def.setUri(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected SagaOptionDefinition doParseSagaOptionDefinition() throws IOException, XmlPullParserException {
        return doParse(new SagaOptionDefinition(), (def, key, val) -> {
            switch (key) {
                case "optionName": def.setOptionName(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "expressionDefinition": def.setExpression(doParseExpressionDefinition()); break;
                case "constant": def.setExpression(doParseConstantExpression()); break;
                case "exchangeProperty": def.setExpression(doParseExchangePropertyExpression()); break;
                case "groovy": def.setExpression(doParseGroovyExpression()); break;
                case "header": def.setExpression(doParseHeaderExpression()); break;
                case "hl7terser": def.setExpression(doParseHl7TerserExpression()); break;
                case "jsonpath": def.setExpression(doParseJsonPathExpression()); break;
                case "language": def.setExpression(doParseLanguageExpression()); break;
                case "method": def.setExpression(doParseMethodCallExpression()); break;
                case "mvel": def.setExpression(doParseMvelExpression()); break;
                case "ognl": def.setExpression(doParseOgnlExpression()); break;
                case "ref": def.setExpression(doParseRefExpression()); break;
                case "simple": def.setExpression(doParseSimpleExpression()); break;
                case "spel": def.setExpression(doParseSpELExpression()); break;
                case "tokenize": def.setExpression(doParseTokenizerExpression()); break;
                case "xtokenize": def.setExpression(doParseXMLTokenizerExpression()); break;
                case "xpath": def.setExpression(doParseXPathExpression()); break;
                case "xquery": def.setExpression(doParseXQueryExpression()); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected SamplingDefinition doParseSamplingDefinition() throws IOException, XmlPullParserException {
        return doParse(new SamplingDefinition(), (def, key, val) -> {
            switch (key) {
                case "messageFrequency": def.setMessageFrequency(java.lang.Long.valueOf(val)); break;
                case "samplePeriod": def.setSamplePeriod(java.lang.Long.valueOf(val)); break;
                case "units": def.setUnits(java.util.concurrent.TimeUnit.valueOf(val)); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ScriptDefinition doParseScriptDefinition() throws IOException, XmlPullParserException {
        return doParse(new ScriptDefinition(),
            processorDefinitionAttributeHandler(), expressionNodeElementHandler(), noValueHandler());
    }
    protected SetBodyDefinition doParseSetBodyDefinition() throws IOException, XmlPullParserException {
        return doParse(new SetBodyDefinition(),
            processorDefinitionAttributeHandler(), expressionNodeElementHandler(), noValueHandler());
    }
    protected SetExchangePatternDefinition doParseSetExchangePatternDefinition() throws IOException, XmlPullParserException {
        return doParse(new SetExchangePatternDefinition(), (def, key, val) -> {
            switch (key) {
                case "pattern": def.setPattern(org.apache.camel.ExchangePattern.valueOf(val)); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected SetHeaderDefinition doParseSetHeaderDefinition() throws IOException, XmlPullParserException {
        return doParse(new SetHeaderDefinition(), (def, key, val) -> {
            switch (key) {
                case "name": def.setName(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected SetPropertyDefinition doParseSetPropertyDefinition() throws IOException, XmlPullParserException {
        return doParse(new SetPropertyDefinition(), (def, key, val) -> {
            switch (key) {
                case "name": def.setName(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected SortDefinition doParseSortDefinition() throws IOException, XmlPullParserException {
        return doParse(new SortDefinition(), (def, key, val) -> {
            switch (key) {
                case "comparatorRef": def.setComparatorRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, expressionNodeElementHandler(), noValueHandler());
    }
    protected SplitDefinition doParseSplitDefinition() throws IOException, XmlPullParserException {
        return doParse(new SplitDefinition(), (def, key, val) -> {
            switch (key) {
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "onPrepareRef": def.setOnPrepareRef(val); break;
                case "parallelAggregate": def.setParallelAggregate(java.lang.Boolean.valueOf(val)); break;
                case "parallelProcessing": def.setParallelProcessing(java.lang.Boolean.valueOf(val)); break;
                case "shareUnitOfWork": def.setShareUnitOfWork(java.lang.Boolean.valueOf(val)); break;
                case "stopOnAggregateException": def.setStopOnAggregateException(java.lang.Boolean.valueOf(val)); break;
                case "stopOnException": def.setStopOnException(java.lang.Boolean.valueOf(val)); break;
                case "strategyMethodAllowNull": def.setStrategyMethodAllowNull(java.lang.Boolean.valueOf(val)); break;
                case "strategyMethodName": def.setStrategyMethodName(val); break;
                case "strategyRef": def.setStrategyRef(val); break;
                case "streaming": def.setStreaming(java.lang.Boolean.valueOf(val)); break;
                case "timeout": def.setTimeout(java.lang.Long.valueOf(val)); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputExpressionNodeElementHandler(), noValueHandler());
    }
    protected StepDefinition doParseStepDefinition() throws IOException, XmlPullParserException {
        return doParse(new StepDefinition(),
            processorDefinitionAttributeHandler(), outputDefinitionElementHandler(), noValueHandler());
    }
    protected StopDefinition doParseStopDefinition() throws IOException, XmlPullParserException {
        return doParse(new StopDefinition(),
            processorDefinitionAttributeHandler(), optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ThreadPoolProfileDefinition doParseThreadPoolProfileDefinition() throws IOException, XmlPullParserException {
        return doParse(new ThreadPoolProfileDefinition(), (def, key, val) -> {
            switch (key) {
                case "allowCoreThreadTimeOut": def.setAllowCoreThreadTimeOut(val); break;
                case "defaultProfile": def.setDefaultProfile(java.lang.Boolean.valueOf(val)); break;
                case "keepAliveTime": def.setKeepAliveTime(val); break;
                case "maxPoolSize": def.setMaxPoolSize(val); break;
                case "maxQueueSize": def.setMaxQueueSize(val); break;
                case "poolSize": def.setPoolSize(val); break;
                case "rejectedPolicy": def.setRejectedPolicy(val); break;
                case "timeUnit": def.setTimeUnit(java.util.concurrent.TimeUnit.valueOf(val)); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ThreadsDefinition doParseThreadsDefinition() throws IOException, XmlPullParserException {
        return doParse(new ThreadsDefinition(), (def, key, val) -> {
            switch (key) {
                case "allowCoreThreadTimeOut": def.setAllowCoreThreadTimeOut(val); break;
                case "callerRunsWhenRejected": def.setCallerRunsWhenRejected(val); break;
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "keepAliveTime": def.setKeepAliveTime(val); break;
                case "maxPoolSize": def.setMaxPoolSize(val); break;
                case "maxQueueSize": def.setMaxQueueSize(val); break;
                case "poolSize": def.setPoolSize(val); break;
                case "rejectedPolicy": def.setRejectedPolicy(org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy.valueOf(val)); break;
                case "threadName": def.setThreadName(val); break;
                case "timeUnit": def.setTimeUnit(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ThrottleDefinition doParseThrottleDefinition() throws IOException, XmlPullParserException {
        return doParse(new ThrottleDefinition(), (def, key, val) -> {
            switch (key) {
                case "asyncDelayed": def.setAsyncDelayed(java.lang.Boolean.valueOf(val)); break;
                case "callerRunsWhenRejected": def.setCallerRunsWhenRejected(java.lang.Boolean.valueOf(val)); break;
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "rejectExecution": def.setRejectExecution(java.lang.Boolean.valueOf(val)); break;
                case "timePeriodMillis": def.setTimePeriodMillis(java.lang.Long.valueOf(val)); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "correlationExpression": def.setCorrelationExpression(doParseExpressionSubElementDefinition()); break;
                default: return expressionNodeElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ThrowExceptionDefinition doParseThrowExceptionDefinition() throws IOException, XmlPullParserException {
        return doParse(new ThrowExceptionDefinition(), (def, key, val) -> {
            switch (key) {
                case "exceptionType": def.setExceptionType(val); break;
                case "message": def.setMessage(val); break;
                case "ref": def.setRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ToDefinition doParseToDefinition() throws IOException, XmlPullParserException {
        return doParse(new ToDefinition(), (def, key, val) -> {
            switch (key) {
                case "pattern": def.setPattern(val); break;
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected ToDynamicDefinition doParseToDynamicDefinition() throws IOException, XmlPullParserException {
        return doParse(new ToDynamicDefinition(), (def, key, val) -> {
            switch (key) {
                case "allowOptimisedComponents": def.setAllowOptimisedComponents(val); break;
                case "cacheSize": def.setCacheSize(val); break;
                case "ignoreInvalidEndpoint": def.setIgnoreInvalidEndpoint(val); break;
                case "pattern": def.setPattern(val); break;
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, optionalIdentifiedDefinitionElementHandler(), noValueHandler());
    }
    protected TransactedDefinition doParseTransactedDefinition() throws IOException, XmlPullParserException {
        return doParse(new TransactedDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, outputDefinitionElementHandler(), noValueHandler());
    }
    protected TransformDefinition doParseTransformDefinition() throws IOException, XmlPullParserException {
        return doParse(new TransformDefinition(),
            processorDefinitionAttributeHandler(), expressionNodeElementHandler(), noValueHandler());
    }
    protected TryDefinition doParseTryDefinition() throws IOException, XmlPullParserException {
        return doParse(new TryDefinition(),
            processorDefinitionAttributeHandler(), outputDefinitionElementHandler(), noValueHandler());
    }
    protected UnmarshalDefinition doParseUnmarshalDefinition() throws IOException, XmlPullParserException {
        return doParse(new UnmarshalDefinition(),
            processorDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "any23": def.setDataFormatType(doParseAny23DataFormat()); break;
                case "asn1": def.setDataFormatType(doParseASN1DataFormat()); break;
                case "avro": def.setDataFormatType(doParseAvroDataFormat()); break;
                case "barcode": def.setDataFormatType(doParseBarcodeDataFormat()); break;
                case "base64": def.setDataFormatType(doParseBase64DataFormat()); break;
                case "beanio": def.setDataFormatType(doParseBeanioDataFormat()); break;
                case "bindy": def.setDataFormatType(doParseBindyDataFormat()); break;
                case "cbor": def.setDataFormatType(doParseCBORDataFormat()); break;
                case "crypto": def.setDataFormatType(doParseCryptoDataFormat()); break;
                case "csv": def.setDataFormatType(doParseCsvDataFormat()); break;
                case "custom": def.setDataFormatType(doParseCustomDataFormat()); break;
                case "fhirJson": def.setDataFormatType(doParseFhirJsonDataFormat()); break;
                case "fhirXml": def.setDataFormatType(doParseFhirXmlDataFormat()); break;
                case "flatpack": def.setDataFormatType(doParseFlatpackDataFormat()); break;
                case "grok": def.setDataFormatType(doParseGrokDataFormat()); break;
                case "gzip": def.setDataFormatType(doParseGzipDataFormat()); break;
                case "hl7": def.setDataFormatType(doParseHL7DataFormat()); break;
                case "ical": def.setDataFormatType(doParseIcalDataFormat()); break;
                case "jacksonxml": def.setDataFormatType(doParseJacksonXMLDataFormat()); break;
                case "jaxb": def.setDataFormatType(doParseJaxbDataFormat()); break;
                case "json": def.setDataFormatType(doParseJsonDataFormat()); break;
                case "jsonApi": def.setDataFormatType(doParseJsonApiDataFormat()); break;
                case "lzf": def.setDataFormatType(doParseLZFDataFormat()); break;
                case "mimeMultipart": def.setDataFormatType(doParseMimeMultipartDataFormat()); break;
                case "protobuf": def.setDataFormatType(doParseProtobufDataFormat()); break;
                case "rss": def.setDataFormatType(doParseRssDataFormat()); break;
                case "secureXML": def.setDataFormatType(doParseXMLSecurityDataFormat()); break;
                case "soapjaxb": def.setDataFormatType(doParseSoapJaxbDataFormat()); break;
                case "syslog": def.setDataFormatType(doParseSyslogDataFormat()); break;
                case "tarfile": def.setDataFormatType(doParseTarFileDataFormat()); break;
                case "thrift": def.setDataFormatType(doParseThriftDataFormat()); break;
                case "tidyMarkup": def.setDataFormatType(doParseTidyMarkupDataFormat()); break;
                case "univocity-csv": def.setDataFormatType(doParseUniVocityCsvDataFormat()); break;
                case "univocity-fixed": def.setDataFormatType(doParseUniVocityFixedWidthDataFormat()); break;
                case "univocity-tsv": def.setDataFormatType(doParseUniVocityTsvDataFormat()); break;
                case "xmlrpc": def.setDataFormatType(doParseXmlRpcDataFormat()); break;
                case "xstream": def.setDataFormatType(doParseXStreamDataFormat()); break;
                case "pgp": def.setDataFormatType(doParsePGPDataFormat()); break;
                case "yaml": def.setDataFormatType(doParseYAMLDataFormat()); break;
                case "zip": def.setDataFormatType(doParseZipDeflaterDataFormat()); break;
                case "zipfile": def.setDataFormatType(doParseZipFileDataFormat()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ValidateDefinition doParseValidateDefinition() throws IOException, XmlPullParserException {
        return doParse(new ValidateDefinition(),
            processorDefinitionAttributeHandler(), expressionNodeElementHandler(), noValueHandler());
    }
    protected WhenSkipSendToEndpointDefinition doParseWhenSkipSendToEndpointDefinition() throws IOException, XmlPullParserException {
        return doParse(new WhenSkipSendToEndpointDefinition(),
            processorDefinitionAttributeHandler(), outputExpressionNodeElementHandler(), noValueHandler());
    }
    protected WireTapDefinition doParseWireTapDefinition() throws IOException, XmlPullParserException {
        return doParse(new WireTapDefinition(), (def, key, val) -> {
            switch (key) {
                case "allowOptimisedComponents": def.setAllowOptimisedComponents(val); break;
                case "cacheSize": def.setCacheSize(val); break;
                case "copy": def.setCopy(val); break;
                case "dynamicUri": def.setDynamicUri(val); break;
                case "executorServiceRef": def.setExecutorServiceRef(val); break;
                case "ignoreInvalidEndpoint": def.setIgnoreInvalidEndpoint(val); break;
                case "processorRef": def.setNewExchangeProcessorRef(val); break;
                case "onPrepareRef": def.setOnPrepareRef(val); break;
                case "pattern": def.setPattern(val); break;
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "setHeader": doAdd(doParseSetHeaderDefinition(), def.getHeaders(), def::setHeaders); break;
                case "body": def.setNewExchangeExpression(doParseExpressionSubElementDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected BlacklistServiceCallServiceFilterConfiguration doParseBlacklistServiceCallServiceFilterConfiguration() throws IOException, XmlPullParserException {
        return doParse(new BlacklistServiceCallServiceFilterConfiguration(),
            identifiedTypeAttributeHandler(), (def, key) -> {
            switch (key) {
                case "servers": doAdd(doParseText(), def.getServers(), def::setServers); break;
                default: return serviceCallConfigurationElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected CachingServiceCallServiceDiscoveryConfiguration doParseCachingServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new CachingServiceCallServiceDiscoveryConfiguration(), (def, key, val) -> {
            switch (key) {
                case "timeout": def.setTimeout(val); break;
                case "units": def.setUnits(java.util.concurrent.TimeUnit.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "consulServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseConsulServiceCallServiceDiscoveryConfiguration()); break;
                case "dnsServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseDnsServiceCallServiceDiscoveryConfiguration()); break;
                case "etcdServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseEtcdServiceCallServiceDiscoveryConfiguration()); break;
                case "kubernetesServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseKubernetesServiceCallServiceDiscoveryConfiguration()); break;
                case "combinedServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseCombinedServiceCallServiceDiscoveryConfiguration()); break;
                case "staticServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseStaticServiceCallServiceDiscoveryConfiguration()); break;
                default: return serviceCallConfigurationElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ServiceCallServiceDiscoveryConfiguration doParseServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new ServiceCallServiceDiscoveryConfiguration(),
            identifiedTypeAttributeHandler(), serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected CombinedServiceCallServiceDiscoveryConfiguration doParseCombinedServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new CombinedServiceCallServiceDiscoveryConfiguration(),
            identifiedTypeAttributeHandler(), (def, key) -> {
            switch (key) {
                case "consulServiceDiscovery": doAdd(doParseConsulServiceCallServiceDiscoveryConfiguration(), def.getServiceDiscoveryConfigurations(), def::setServiceDiscoveryConfigurations); break;
                case "dnsServiceDiscovery": doAdd(doParseDnsServiceCallServiceDiscoveryConfiguration(), def.getServiceDiscoveryConfigurations(), def::setServiceDiscoveryConfigurations); break;
                case "etcdServiceDiscovery": doAdd(doParseEtcdServiceCallServiceDiscoveryConfiguration(), def.getServiceDiscoveryConfigurations(), def::setServiceDiscoveryConfigurations); break;
                case "kubernetesServiceDiscovery": doAdd(doParseKubernetesServiceCallServiceDiscoveryConfiguration(), def.getServiceDiscoveryConfigurations(), def::setServiceDiscoveryConfigurations); break;
                case "staticServiceDiscovery": doAdd(doParseStaticServiceCallServiceDiscoveryConfiguration(), def.getServiceDiscoveryConfigurations(), def::setServiceDiscoveryConfigurations); break;
                case "cachingServiceDiscovery": doAdd(doParseCachingServiceCallServiceDiscoveryConfiguration(), def.getServiceDiscoveryConfigurations(), def::setServiceDiscoveryConfigurations); break;
                default: return serviceCallConfigurationElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected CombinedServiceCallServiceFilterConfiguration doParseCombinedServiceCallServiceFilterConfiguration() throws IOException, XmlPullParserException {
        return doParse(new CombinedServiceCallServiceFilterConfiguration(),
            identifiedTypeAttributeHandler(), (def, key) -> {
            switch (key) {
                case "blacklistServiceFilter": doAdd(doParseBlacklistServiceCallServiceFilterConfiguration(), def.getServiceFilterConfigurations(), def::setServiceFilterConfigurations); break;
                case "customServiceFilter": doAdd(doParseCustomServiceCallServiceFilterConfiguration(), def.getServiceFilterConfigurations(), def::setServiceFilterConfigurations); break;
                case "healthyServiceFilter": doAdd(doParseHealthyServiceCallServiceFilterConfiguration(), def.getServiceFilterConfigurations(), def::setServiceFilterConfigurations); break;
                case "passThroughServiceFilter": doAdd(doParsePassThroughServiceCallServiceFilterConfiguration(), def.getServiceFilterConfigurations(), def::setServiceFilterConfigurations); break;
                default: return serviceCallConfigurationElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ServiceCallServiceFilterConfiguration doParseServiceCallServiceFilterConfiguration() throws IOException, XmlPullParserException {
        return doParse(new ServiceCallServiceFilterConfiguration(),
            identifiedTypeAttributeHandler(), serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected ConsulServiceCallServiceDiscoveryConfiguration doParseConsulServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new ConsulServiceCallServiceDiscoveryConfiguration(), (def, key, val) -> {
            switch (key) {
                case "aclToken": def.setAclToken(val); break;
                case "blockSeconds": def.setBlockSeconds(java.lang.Integer.valueOf(val)); break;
                case "connectTimeoutMillis": def.setConnectTimeoutMillis(java.lang.Long.valueOf(val)); break;
                case "datacenter": def.setDatacenter(val); break;
                case "password": def.setPassword(val); break;
                case "readTimeoutMillis": def.setReadTimeoutMillis(java.lang.Long.valueOf(val)); break;
                case "url": def.setUrl(val); break;
                case "userName": def.setUserName(val); break;
                case "writeTimeoutMillis": def.setWriteTimeoutMillis(java.lang.Long.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected CustomServiceCallServiceFilterConfiguration doParseCustomServiceCallServiceFilterConfiguration() throws IOException, XmlPullParserException {
        return doParse(new CustomServiceCallServiceFilterConfiguration(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setServiceFilterRef(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected DefaultServiceCallServiceLoadBalancerConfiguration doParseDefaultServiceCallServiceLoadBalancerConfiguration() throws IOException, XmlPullParserException {
        return doParse(new DefaultServiceCallServiceLoadBalancerConfiguration(),
            identifiedTypeAttributeHandler(), serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected DnsServiceCallServiceDiscoveryConfiguration doParseDnsServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new DnsServiceCallServiceDiscoveryConfiguration(), (def, key, val) -> {
            switch (key) {
                case "domain": def.setDomain(val); break;
                case "proto": def.setProto(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected EtcdServiceCallServiceDiscoveryConfiguration doParseEtcdServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new EtcdServiceCallServiceDiscoveryConfiguration(), (def, key, val) -> {
            switch (key) {
                case "password": def.setPassword(val); break;
                case "servicePath": def.setServicePath(val); break;
                case "timeout": def.setTimeout(java.lang.Long.valueOf(val)); break;
                case "type": def.setType(val); break;
                case "uris": def.setUris(val); break;
                case "userName": def.setUserName(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected HealthyServiceCallServiceFilterConfiguration doParseHealthyServiceCallServiceFilterConfiguration() throws IOException, XmlPullParserException {
        return doParse(new HealthyServiceCallServiceFilterConfiguration(),
            identifiedTypeAttributeHandler(), serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected KubernetesServiceCallServiceDiscoveryConfiguration doParseKubernetesServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new KubernetesServiceCallServiceDiscoveryConfiguration(), (def, key, val) -> {
            switch (key) {
                case "apiVersion": def.setApiVersion(val); break;
                case "caCertData": def.setCaCertData(val); break;
                case "caCertFile": def.setCaCertFile(val); break;
                case "clientCertData": def.setClientCertData(val); break;
                case "clientCertFile": def.setClientCertFile(val); break;
                case "clientKeyAlgo": def.setClientKeyAlgo(val); break;
                case "clientKeyData": def.setClientKeyData(val); break;
                case "clientKeyFile": def.setClientKeyFile(val); break;
                case "clientKeyPassphrase": def.setClientKeyPassphrase(val); break;
                case "dnsDomain": def.setDnsDomain(val); break;
                case "lookup": def.setLookup(val); break;
                case "masterUrl": def.setMasterUrl(val); break;
                case "namespace": def.setNamespace(val); break;
                case "oauthToken": def.setOauthToken(val); break;
                case "password": def.setPassword(val); break;
                case "portName": def.setPortName(val); break;
                case "portProtocol": def.setPortProtocol(val); break;
                case "trustCerts": def.setTrustCerts(java.lang.Boolean.valueOf(val)); break;
                case "username": def.setUsername(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected PassThroughServiceCallServiceFilterConfiguration doParsePassThroughServiceCallServiceFilterConfiguration() throws IOException, XmlPullParserException {
        return doParse(new PassThroughServiceCallServiceFilterConfiguration(),
            identifiedTypeAttributeHandler(), serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected RibbonServiceCallServiceLoadBalancerConfiguration doParseRibbonServiceCallServiceLoadBalancerConfiguration() throws IOException, XmlPullParserException {
        return doParse(new RibbonServiceCallServiceLoadBalancerConfiguration(), (def, key, val) -> {
            switch (key) {
                case "clientName": def.setClientName(val); break;
                case "namespace": def.setNamespace(val); break;
                case "password": def.setPassword(val); break;
                case "username": def.setUsername(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected ServiceCallConfigurationDefinition doParseServiceCallConfigurationDefinition() throws IOException, XmlPullParserException {
        return doParse(new ServiceCallConfigurationDefinition(), (def, key, val) -> {
            switch (key) {
                case "component": def.setComponent(val); break;
                case "expressionRef": def.setExpressionRef(val); break;
                case "loadBalancerRef": def.setLoadBalancerRef(val); break;
                case "pattern": def.setPattern(org.apache.camel.ExchangePattern.valueOf(val)); break;
                case "serviceChooserRef": def.setServiceChooserRef(val); break;
                case "serviceDiscoveryRef": def.setServiceDiscoveryRef(val); break;
                case "serviceFilterRef": def.setServiceFilterRef(val); break;
                case "uri": def.setUri(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "expression": def.setExpressionConfiguration(doParseServiceCallExpressionConfiguration()); break;
                case "ribbonLoadBalancer": def.setLoadBalancerConfiguration(doParseRibbonServiceCallServiceLoadBalancerConfiguration()); break;
                case "defaultLoadBalancer": def.setLoadBalancerConfiguration(doParseDefaultServiceCallServiceLoadBalancerConfiguration()); break;
                case "cachingServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseCachingServiceCallServiceDiscoveryConfiguration()); break;
                case "combinedServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseCombinedServiceCallServiceDiscoveryConfiguration()); break;
                case "consulServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseConsulServiceCallServiceDiscoveryConfiguration()); break;
                case "dnsServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseDnsServiceCallServiceDiscoveryConfiguration()); break;
                case "etcdServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseEtcdServiceCallServiceDiscoveryConfiguration()); break;
                case "kubernetesServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseKubernetesServiceCallServiceDiscoveryConfiguration()); break;
                case "staticServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseStaticServiceCallServiceDiscoveryConfiguration()); break;
                case "zookeeperServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseZooKeeperServiceCallServiceDiscoveryConfiguration()); break;
                case "blacklistServiceFilter": def.setServiceFilterConfiguration(doParseBlacklistServiceCallServiceFilterConfiguration()); break;
                case "combinedServiceFilter": def.setServiceFilterConfiguration(doParseCombinedServiceCallServiceFilterConfiguration()); break;
                case "customServiceFilter": def.setServiceFilterConfiguration(doParseCustomServiceCallServiceFilterConfiguration()); break;
                case "healthyServiceFilter": def.setServiceFilterConfiguration(doParseHealthyServiceCallServiceFilterConfiguration()); break;
                case "passThroughServiceFilter": def.setServiceFilterConfiguration(doParsePassThroughServiceCallServiceFilterConfiguration()); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected ServiceCallServiceLoadBalancerConfiguration doParseServiceCallServiceLoadBalancerConfiguration() throws IOException, XmlPullParserException {
        return doParse(new ServiceCallServiceLoadBalancerConfiguration(),
            identifiedTypeAttributeHandler(), serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected ServiceCallExpressionConfiguration doParseServiceCallExpressionConfiguration() throws IOException, XmlPullParserException {
        return doParse(new ServiceCallExpressionConfiguration(), (def, key, val) -> {
            switch (key) {
                case "hostHeader": def.setHostHeader(val); break;
                case "portHeader": def.setPortHeader(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "expressionDefinition": def.setExpressionType(doParseExpressionDefinition()); break;
                case "constant": def.setExpressionType(doParseConstantExpression()); break;
                case "exchangeProperty": def.setExpressionType(doParseExchangePropertyExpression()); break;
                case "groovy": def.setExpressionType(doParseGroovyExpression()); break;
                case "header": def.setExpressionType(doParseHeaderExpression()); break;
                case "hl7terser": def.setExpressionType(doParseHl7TerserExpression()); break;
                case "jsonpath": def.setExpressionType(doParseJsonPathExpression()); break;
                case "language": def.setExpressionType(doParseLanguageExpression()); break;
                case "method": def.setExpressionType(doParseMethodCallExpression()); break;
                case "mvel": def.setExpressionType(doParseMvelExpression()); break;
                case "ognl": def.setExpressionType(doParseOgnlExpression()); break;
                case "ref": def.setExpressionType(doParseRefExpression()); break;
                case "simple": def.setExpressionType(doParseSimpleExpression()); break;
                case "spel": def.setExpressionType(doParseSpELExpression()); break;
                case "tokenize": def.setExpressionType(doParseTokenizerExpression()); break;
                case "xtokenize": def.setExpressionType(doParseXMLTokenizerExpression()); break;
                case "xpath": def.setExpressionType(doParseXPathExpression()); break;
                case "xquery": def.setExpressionType(doParseXQueryExpression()); break;
                default: return serviceCallConfigurationElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ServiceCallDefinition doParseServiceCallDefinition() throws IOException, XmlPullParserException {
        return doParse(new ServiceCallDefinition(), (def, key, val) -> {
            switch (key) {
                case "component": def.setComponent(val); break;
                case "configurationRef": def.setConfigurationRef(val); break;
                case "expressionRef": def.setExpressionRef(val); break;
                case "loadBalancerRef": def.setLoadBalancerRef(val); break;
                case "name": def.setName(val); break;
                case "pattern": def.setPattern(org.apache.camel.ExchangePattern.valueOf(val)); break;
                case "serviceChooserRef": def.setServiceChooserRef(val); break;
                case "serviceDiscoveryRef": def.setServiceDiscoveryRef(val); break;
                case "serviceFilterRef": def.setServiceFilterRef(val); break;
                case "uri": def.setUri(val); break;
                default: return processorDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "expressionConfiguration": def.setExpressionConfiguration(doParseServiceCallExpressionConfiguration()); break;
                case "ribbonLoadBalancer": def.setLoadBalancerConfiguration(doParseRibbonServiceCallServiceLoadBalancerConfiguration()); break;
                case "defaultLoadBalancer": def.setLoadBalancerConfiguration(doParseDefaultServiceCallServiceLoadBalancerConfiguration()); break;
                case "cachingServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseCachingServiceCallServiceDiscoveryConfiguration()); break;
                case "combinedServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseCombinedServiceCallServiceDiscoveryConfiguration()); break;
                case "consulServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseConsulServiceCallServiceDiscoveryConfiguration()); break;
                case "dnsServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseDnsServiceCallServiceDiscoveryConfiguration()); break;
                case "etcdServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseEtcdServiceCallServiceDiscoveryConfiguration()); break;
                case "kubernetesServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseKubernetesServiceCallServiceDiscoveryConfiguration()); break;
                case "staticServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseStaticServiceCallServiceDiscoveryConfiguration()); break;
                case "zookeeperServiceDiscovery": def.setServiceDiscoveryConfiguration(doParseZooKeeperServiceCallServiceDiscoveryConfiguration()); break;
                case "blacklistServiceFilter": def.setServiceFilterConfiguration(doParseBlacklistServiceCallServiceFilterConfiguration()); break;
                case "combinedServiceFilter": def.setServiceFilterConfiguration(doParseCombinedServiceCallServiceFilterConfiguration()); break;
                case "customServiceFilter": def.setServiceFilterConfiguration(doParseCustomServiceCallServiceFilterConfiguration()); break;
                case "healthyServiceFilter": def.setServiceFilterConfiguration(doParseHealthyServiceCallServiceFilterConfiguration()); break;
                case "passThroughServiceFilter": def.setServiceFilterConfiguration(doParsePassThroughServiceCallServiceFilterConfiguration()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ServiceCallServiceChooserConfiguration doParseServiceCallServiceChooserConfiguration() throws IOException, XmlPullParserException {
        return doParse(new ServiceCallServiceChooserConfiguration(),
            identifiedTypeAttributeHandler(), serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected StaticServiceCallServiceDiscoveryConfiguration doParseStaticServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new StaticServiceCallServiceDiscoveryConfiguration(),
            identifiedTypeAttributeHandler(), (def, key) -> {
            switch (key) {
                case "servers": doAdd(doParseText(), def.getServers(), def::setServers); break;
                default: return serviceCallConfigurationElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected ZooKeeperServiceCallServiceDiscoveryConfiguration doParseZooKeeperServiceCallServiceDiscoveryConfiguration() throws IOException, XmlPullParserException {
        return doParse(new ZooKeeperServiceCallServiceDiscoveryConfiguration(), (def, key, val) -> {
            switch (key) {
                case "basePath": def.setBasePath(val); break;
                case "connectionTimeout": def.setConnectionTimeout(val); break;
                case "namespace": def.setNamespace(val); break;
                case "nodes": def.setNodes(val); break;
                case "reconnectBaseSleepTime": def.setReconnectBaseSleepTime(val); break;
                case "reconnectMaxRetries": def.setReconnectMaxRetries(val); break;
                case "reconnectMaxSleepTime": def.setReconnectMaxSleepTime(val); break;
                case "sessionTimeout": def.setSessionTimeout(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, serviceCallConfigurationElementHandler(), noValueHandler());
    }
    protected BatchResequencerConfig doParseBatchResequencerConfig() throws IOException, XmlPullParserException {
        return doParse(new BatchResequencerConfig(), (def, key, val) -> {
            switch (key) {
                case "allowDuplicates": def.setAllowDuplicates(val); break;
                case "batchSize": def.setBatchSize(val); break;
                case "batchTimeout": def.setBatchTimeout(val); break;
                case "ignoreInvalidExchanges": def.setIgnoreInvalidExchanges(val); break;
                case "reverse": def.setReverse(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected StreamResequencerConfig doParseStreamResequencerConfig() throws IOException, XmlPullParserException {
        return doParse(new StreamResequencerConfig(), (def, key, val) -> {
            switch (key) {
                case "capacity": def.setCapacity(val); break;
                case "comparatorRef": def.setComparatorRef(val); break;
                case "deliveryAttemptInterval": def.setDeliveryAttemptInterval(val); break;
                case "ignoreInvalidExchanges": def.setIgnoreInvalidExchanges(val); break;
                case "rejectOld": def.setRejectOld(val); break;
                case "timeout": def.setTimeout(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected ASN1DataFormat doParseASN1DataFormat() throws IOException, XmlPullParserException {
        return doParse(new ASN1DataFormat(), (def, key, val) -> {
            switch (key) {
                case "clazzName": def.setClazzName(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "usingIterator": def.setUsingIterator(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected Any23DataFormat doParseAny23DataFormat() throws IOException, XmlPullParserException {
        return doParse(new Any23DataFormat(), (def, key, val) -> {
            switch (key) {
                case "baseURI": def.setBaseURI(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "outputFormat": def.setOutputFormat(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "configuration": def.setConfiguration(unmarshal(new org.apache.camel.model.PropertyDescriptionsAdapter(), doParsePropertyDefinitions())); break;
                case "extractors": doAdd(doParseText(), def.getExtractors(), def::setExtractors); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected AvroDataFormat doParseAvroDataFormat() throws IOException, XmlPullParserException {
        return doParse(new AvroDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "instanceClassName": def.setInstanceClassName(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected BarcodeDataFormat doParseBarcodeDataFormat() throws IOException, XmlPullParserException {
        return doParse(new BarcodeDataFormat(), (def, key, val) -> {
            switch (key) {
                case "barcodeFormat": def.setBarcodeFormat(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "height": def.setHeight(val); break;
                case "imageType": def.setImageType(val); break;
                case "width": def.setWidth(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected Base64DataFormat doParseBase64DataFormat() throws IOException, XmlPullParserException {
        return doParse(new Base64DataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "lineLength": def.setLineLength(val); break;
                case "lineSeparator": def.setLineSeparator(val); break;
                case "urlSafe": def.setUrlSafe(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected BeanioDataFormat doParseBeanioDataFormat() throws IOException, XmlPullParserException {
        return doParse(new BeanioDataFormat(), (def, key, val) -> {
            switch (key) {
                case "beanReaderErrorHandlerType": def.setBeanReaderErrorHandlerType(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "encoding": def.setEncoding(val); break;
                case "ignoreInvalidRecords": def.setIgnoreInvalidRecords(val); break;
                case "ignoreUnexpectedRecords": def.setIgnoreUnexpectedRecords(val); break;
                case "ignoreUnidentifiedRecords": def.setIgnoreUnidentifiedRecords(val); break;
                case "mapping": def.setMapping(val); break;
                case "streamName": def.setStreamName(val); break;
                case "unmarshalSingleObject": def.setUnmarshalSingleObject(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected BindyDataFormat doParseBindyDataFormat() throws IOException, XmlPullParserException {
        return doParse(new BindyDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowEmptyStream": def.setAllowEmptyStream(java.lang.Boolean.valueOf(val)); break;
                case "classType": def.setClassType(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "locale": def.setLocale(val); break;
                case "type": def.setType(org.apache.camel.model.dataformat.BindyType.valueOf(val)); break;
                case "unwrapSingleInstance": def.setUnwrapSingleInstance(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected CBORDataFormat doParseCBORDataFormat() throws IOException, XmlPullParserException {
        return doParse(new CBORDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowJmsType": def.setAllowJmsType(val); break;
                case "allowUnmarshallType": def.setAllowUnmarshallType(val); break;
                case "collectionTypeName": def.setCollectionTypeName(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "disableFeatures": def.setDisableFeatures(val); break;
                case "enableFeatures": def.setEnableFeatures(val); break;
                case "objectMapper": def.setObjectMapper(val); break;
                case "prettyPrint": def.setPrettyPrint(val); break;
                case "unmarshalTypeName": def.setUnmarshalTypeName(val); break;
                case "useDefaultObjectMapper": def.setUseDefaultObjectMapper(val); break;
                case "useList": def.setUseList(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected CryptoDataFormat doParseCryptoDataFormat() throws IOException, XmlPullParserException {
        return doParse(new CryptoDataFormat(), (def, key, val) -> {
            switch (key) {
                case "algorithm": def.setAlgorithm(val); break;
                case "algorithmParameterRef": def.setAlgorithmParameterRef(val); break;
                case "buffersize": def.setBuffersize(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "cryptoProvider": def.setCryptoProvider(val); break;
                case "initVectorRef": def.setInitVectorRef(val); break;
                case "inline": def.setInline(val); break;
                case "keyRef": def.setKeyRef(val); break;
                case "macAlgorithm": def.setMacAlgorithm(val); break;
                case "shouldAppendHMAC": def.setShouldAppendHMAC(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected CsvDataFormat doParseCsvDataFormat() throws IOException, XmlPullParserException {
        return doParse(new CsvDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowMissingColumnNames": def.setAllowMissingColumnNames(java.lang.Boolean.valueOf(val)); break;
                case "commentMarker": def.setCommentMarker(val); break;
                case "commentMarkerDisabled": def.setCommentMarkerDisabled(java.lang.Boolean.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "delimiter": def.setDelimiter(val); break;
                case "escape": def.setEscape(val); break;
                case "escapeDisabled": def.setEscapeDisabled(java.lang.Boolean.valueOf(val)); break;
                case "formatName": def.setFormatName(val); break;
                case "formatRef": def.setFormatRef(val); break;
                case "headerDisabled": def.setHeaderDisabled(java.lang.Boolean.valueOf(val)); break;
                case "ignoreEmptyLines": def.setIgnoreEmptyLines(java.lang.Boolean.valueOf(val)); break;
                case "ignoreHeaderCase": def.setIgnoreHeaderCase(java.lang.Boolean.valueOf(val)); break;
                case "ignoreSurroundingSpaces": def.setIgnoreSurroundingSpaces(java.lang.Boolean.valueOf(val)); break;
                case "lazyLoad": def.setLazyLoad(java.lang.Boolean.valueOf(val)); break;
                case "marshallerFactoryRef": def.setMarshallerFactoryRef(val); break;
                case "nullString": def.setNullString(val); break;
                case "nullStringDisabled": def.setNullStringDisabled(java.lang.Boolean.valueOf(val)); break;
                case "quote": def.setQuote(val); break;
                case "quoteDisabled": def.setQuoteDisabled(java.lang.Boolean.valueOf(val)); break;
                case "quoteMode": def.setQuoteMode(val); break;
                case "recordConverterRef": def.setRecordConverterRef(val); break;
                case "recordSeparator": def.setRecordSeparator(val); break;
                case "recordSeparatorDisabled": def.setRecordSeparatorDisabled(val); break;
                case "skipHeaderRecord": def.setSkipHeaderRecord(java.lang.Boolean.valueOf(val)); break;
                case "trailingDelimiter": def.setTrailingDelimiter(java.lang.Boolean.valueOf(val)); break;
                case "trim": def.setTrim(java.lang.Boolean.valueOf(val)); break;
                case "useMaps": def.setUseMaps(java.lang.Boolean.valueOf(val)); break;
                case "useOrderedMaps": def.setUseOrderedMaps(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "header": doAdd(doParseText(), def.getHeader(), def::setHeader); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected CustomDataFormat doParseCustomDataFormat() throws IOException, XmlPullParserException {
        return doParse(new CustomDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "ref": def.setRef(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected DataFormatsDefinition doParseDataFormatsDefinition() throws IOException, XmlPullParserException {
        return doParse(new DataFormatsDefinition(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "any23": doAdd(doParseAny23DataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "asn1": doAdd(doParseASN1DataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "avro": doAdd(doParseAvroDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "barcode": doAdd(doParseBarcodeDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "base64": doAdd(doParseBase64DataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "beanio": doAdd(doParseBeanioDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "bindy": doAdd(doParseBindyDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "cbor": doAdd(doParseCBORDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "crypto": doAdd(doParseCryptoDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "csv": doAdd(doParseCsvDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "custom": doAdd(doParseCustomDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "fhirJson": doAdd(doParseFhirJsonDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "fhirXml": doAdd(doParseFhirXmlDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "flatpack": doAdd(doParseFlatpackDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "grok": doAdd(doParseGrokDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "gzip": doAdd(doParseGzipDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "hl7": doAdd(doParseHL7DataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "ical": doAdd(doParseIcalDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "jacksonxml": doAdd(doParseJacksonXMLDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "jaxb": doAdd(doParseJaxbDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "json": doAdd(doParseJsonDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "jsonApi": doAdd(doParseJsonApiDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "lzf": doAdd(doParseLZFDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "mimeMultipart": doAdd(doParseMimeMultipartDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "protobuf": doAdd(doParseProtobufDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "rss": doAdd(doParseRssDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "secureXML": doAdd(doParseXMLSecurityDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "soapjaxb": doAdd(doParseSoapJaxbDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "syslog": doAdd(doParseSyslogDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "tarfile": doAdd(doParseTarFileDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "thrift": doAdd(doParseThriftDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "tidyMarkup": doAdd(doParseTidyMarkupDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "univocity-csv": doAdd(doParseUniVocityCsvDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "univocity-fixed": doAdd(doParseUniVocityFixedWidthDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "univocity-tsv": doAdd(doParseUniVocityTsvDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "xmlrpc": doAdd(doParseXmlRpcDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "xstream": doAdd(doParseXStreamDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "pgp": doAdd(doParsePGPDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "yaml": doAdd(doParseYAMLDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "zip": doAdd(doParseZipDeflaterDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                case "zipfile": doAdd(doParseZipFileDataFormat(), def.getDataFormats(), def::setDataFormats); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected FhirJsonDataFormat doParseFhirJsonDataFormat() throws IOException, XmlPullParserException {
        return doParse(new FhirJsonDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "dontEncodeElements": def.setDontEncodeElements(asStringSet(val)); break;
                case "dontStripVersionsFromReferencesAtPaths": def.setDontStripVersionsFromReferencesAtPaths(asStringList(val)); break;
                case "encodeElements": def.setEncodeElements(asStringSet(val)); break;
                case "encodeElementsAppliesToChildResourcesOnly": def.setEncodeElementsAppliesToChildResourcesOnly(java.lang.Boolean.valueOf(val)); break;
                case "fhirVersion": def.setFhirVersion(val); break;
                case "omitResourceId": def.setOmitResourceId(java.lang.Boolean.valueOf(val)); break;
                case "overrideResourceIdWithBundleEntryFullUrl": def.setOverrideResourceIdWithBundleEntryFullUrl(java.lang.Boolean.valueOf(val)); break;
                case "prettyPrint": def.setPrettyPrint(java.lang.Boolean.valueOf(val)); break;
                case "serverBaseUrl": def.setServerBaseUrl(val); break;
                case "stripVersionsFromReferences": def.setStripVersionsFromReferences(java.lang.Boolean.valueOf(val)); break;
                case "summaryMode": def.setSummaryMode(java.lang.Boolean.valueOf(val)); break;
                case "suppressNarratives": def.setSuppressNarratives(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected FhirXmlDataFormat doParseFhirXmlDataFormat() throws IOException, XmlPullParserException {
        return doParse(new FhirXmlDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "dontEncodeElements": def.setDontEncodeElements(asStringSet(val)); break;
                case "dontStripVersionsFromReferencesAtPaths": def.setDontStripVersionsFromReferencesAtPaths(asStringList(val)); break;
                case "encodeElements": def.setEncodeElements(asStringSet(val)); break;
                case "encodeElementsAppliesToChildResourcesOnly": def.setEncodeElementsAppliesToChildResourcesOnly(java.lang.Boolean.valueOf(val)); break;
                case "fhirVersion": def.setFhirVersion(val); break;
                case "omitResourceId": def.setOmitResourceId(java.lang.Boolean.valueOf(val)); break;
                case "overrideResourceIdWithBundleEntryFullUrl": def.setOverrideResourceIdWithBundleEntryFullUrl(java.lang.Boolean.valueOf(val)); break;
                case "prettyPrint": def.setPrettyPrint(java.lang.Boolean.valueOf(val)); break;
                case "serverBaseUrl": def.setServerBaseUrl(val); break;
                case "stripVersionsFromReferences": def.setStripVersionsFromReferences(java.lang.Boolean.valueOf(val)); break;
                case "summaryMode": def.setSummaryMode(java.lang.Boolean.valueOf(val)); break;
                case "suppressNarratives": def.setSuppressNarratives(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected FlatpackDataFormat doParseFlatpackDataFormat() throws IOException, XmlPullParserException {
        return doParse(new FlatpackDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowShortLines": def.setAllowShortLines(java.lang.Boolean.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "definition": def.setDefinition(val); break;
                case "delimiter": def.setDelimiter(val); break;
                case "fixed": def.setFixed(java.lang.Boolean.valueOf(val)); break;
                case "ignoreExtraColumns": def.setIgnoreExtraColumns(java.lang.Boolean.valueOf(val)); break;
                case "ignoreFirstRecord": def.setIgnoreFirstRecord(java.lang.Boolean.valueOf(val)); break;
                case "parserFactoryRef": def.setParserFactoryRef(val); break;
                case "textQualifier": def.setTextQualifier(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected GrokDataFormat doParseGrokDataFormat() throws IOException, XmlPullParserException {
        return doParse(new GrokDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowMultipleMatchesPerLine": def.setAllowMultipleMatchesPerLine(java.lang.Boolean.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "flattened": def.setFlattened(java.lang.Boolean.valueOf(val)); break;
                case "namedOnly": def.setNamedOnly(java.lang.Boolean.valueOf(val)); break;
                case "pattern": def.setPattern(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected GzipDataFormat doParseGzipDataFormat() throws IOException, XmlPullParserException {
        return doParse(new GzipDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected HL7DataFormat doParseHL7DataFormat() throws IOException, XmlPullParserException {
        return doParse(new HL7DataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "validate": def.setValidate(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected IcalDataFormat doParseIcalDataFormat() throws IOException, XmlPullParserException {
        return doParse(new IcalDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "validating": def.setValidating(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected JacksonXMLDataFormat doParseJacksonXMLDataFormat() throws IOException, XmlPullParserException {
        return doParse(new JacksonXMLDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowJmsType": def.setAllowJmsType(val); break;
                case "allowUnmarshallType": def.setAllowUnmarshallType(val); break;
                case "collectionTypeName": def.setCollectionTypeName(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "disableFeatures": def.setDisableFeatures(val); break;
                case "enableFeatures": def.setEnableFeatures(val); break;
                case "enableJaxbAnnotationModule": def.setEnableJaxbAnnotationModule(val); break;
                case "include": def.setInclude(val); break;
                case "jsonView": def.setJsonView(asClass(val)); break;
                case "moduleClassNames": def.setModuleClassNames(val); break;
                case "moduleRefs": def.setModuleRefs(val); break;
                case "prettyPrint": def.setPrettyPrint(val); break;
                case "unmarshalTypeName": def.setUnmarshalTypeName(val); break;
                case "useList": def.setUseList(val); break;
                case "xmlMapper": def.setXmlMapper(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected JaxbDataFormat doParseJaxbDataFormat() throws IOException, XmlPullParserException {
        return doParse(new JaxbDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "contextPath": def.setContextPath(val); break;
                case "encoding": def.setEncoding(val); break;
                case "filterNonXmlChars": def.setFilterNonXmlChars(java.lang.Boolean.valueOf(val)); break;
                case "fragment": def.setFragment(java.lang.Boolean.valueOf(val)); break;
                case "ignoreJAXBElement": def.setIgnoreJAXBElement(java.lang.Boolean.valueOf(val)); break;
                case "jaxbProviderProperties": def.setJaxbProviderProperties(val); break;
                case "mustBeJAXBElement": def.setMustBeJAXBElement(java.lang.Boolean.valueOf(val)); break;
                case "namespacePrefixRef": def.setNamespacePrefixRef(val); break;
                case "noNamespaceSchemaLocation": def.setNoNamespaceSchemaLocation(val); break;
                case "objectFactory": def.setObjectFactory(java.lang.Boolean.valueOf(val)); break;
                case "partClass": def.setPartClass(val); break;
                case "partNamespace": def.setPartNamespace(val); break;
                case "prettyPrint": def.setPrettyPrint(java.lang.Boolean.valueOf(val)); break;
                case "schema": def.setSchema(val); break;
                case "schemaLocation": def.setSchemaLocation(val); break;
                case "schemaSeverityLevel": def.setSchemaSeverityLevel(java.lang.Integer.valueOf(val)); break;
                case "xmlStreamWriterWrapper": def.setXmlStreamWriterWrapper(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected JsonApiDataFormat doParseJsonApiDataFormat() throws IOException, XmlPullParserException {
        return doParse(new JsonApiDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "dataFormatTypes": def.setDataFormatTypes(asClassArray(val)); break;
                case "mainFormatType": def.setMainFormatType(asClass(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected JsonDataFormat doParseJsonDataFormat() throws IOException, XmlPullParserException {
        return doParse(new JsonDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowJmsType": def.setAllowJmsType(java.lang.Boolean.valueOf(val)); break;
                case "allowUnmarshallType": def.setAllowUnmarshallType(java.lang.Boolean.valueOf(val)); break;
                case "autoDiscoverObjectMapper": def.setAutoDiscoverObjectMapper(java.lang.Boolean.valueOf(val)); break;
                case "collectionTypeName": def.setCollectionTypeName(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "disableFeatures": def.setDisableFeatures(val); break;
                case "enableFeatures": def.setEnableFeatures(val); break;
                case "enableJaxbAnnotationModule": def.setEnableJaxbAnnotationModule(java.lang.Boolean.valueOf(val)); break;
                case "include": def.setInclude(val); break;
                case "jsonView": def.setJsonView(asClass(val)); break;
                case "library": def.setLibrary(org.apache.camel.model.dataformat.JsonLibrary.valueOf(val)); break;
                case "moduleClassNames": def.setModuleClassNames(val); break;
                case "moduleRefs": def.setModuleRefs(val); break;
                case "objectMapper": def.setObjectMapper(val); break;
                case "permissions": def.setPermissions(val); break;
                case "prettyPrint": def.setPrettyPrint(java.lang.Boolean.valueOf(val)); break;
                case "timezone": def.setTimezone(val); break;
                case "unmarshalTypeName": def.setUnmarshalTypeName(val); break;
                case "useDefaultObjectMapper": def.setUseDefaultObjectMapper(java.lang.Boolean.valueOf(val)); break;
                case "useList": def.setUseList(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected LZFDataFormat doParseLZFDataFormat() throws IOException, XmlPullParserException {
        return doParse(new LZFDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "usingParallelCompression": def.setUsingParallelCompression(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected MimeMultipartDataFormat doParseMimeMultipartDataFormat() throws IOException, XmlPullParserException {
        return doParse(new MimeMultipartDataFormat(), (def, key, val) -> {
            switch (key) {
                case "binaryContent": def.setBinaryContent(java.lang.Boolean.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "headersInline": def.setHeadersInline(java.lang.Boolean.valueOf(val)); break;
                case "includeHeaders": def.setIncludeHeaders(val); break;
                case "multipartSubType": def.setMultipartSubType(val); break;
                case "multipartWithoutAttachment": def.setMultipartWithoutAttachment(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected PGPDataFormat doParsePGPDataFormat() throws IOException, XmlPullParserException {
        return doParse(new PGPDataFormat(), (def, key, val) -> {
            switch (key) {
                case "algorithm": def.setAlgorithm(val); break;
                case "armored": def.setArmored(val); break;
                case "compressionAlgorithm": def.setCompressionAlgorithm(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "hashAlgorithm": def.setHashAlgorithm(val); break;
                case "integrity": def.setIntegrity(val); break;
                case "keyFileName": def.setKeyFileName(val); break;
                case "keyUserid": def.setKeyUserid(val); break;
                case "password": def.setPassword(val); break;
                case "provider": def.setProvider(val); break;
                case "signatureKeyFileName": def.setSignatureKeyFileName(val); break;
                case "signatureKeyRing": def.setSignatureKeyRing(val); break;
                case "signatureKeyUserid": def.setSignatureKeyUserid(val); break;
                case "signaturePassword": def.setSignaturePassword(val); break;
                case "signatureVerificationOption": def.setSignatureVerificationOption(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected ProtobufDataFormat doParseProtobufDataFormat() throws IOException, XmlPullParserException {
        return doParse(new ProtobufDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeFormat": def.setContentTypeFormat(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "instanceClass": def.setInstanceClass(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected RssDataFormat doParseRssDataFormat() throws IOException, XmlPullParserException {
        return doParse(new RssDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected SoapJaxbDataFormat doParseSoapJaxbDataFormat() throws IOException, XmlPullParserException {
        return doParse(new SoapJaxbDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "contextPath": def.setContextPath(val); break;
                case "elementNameStrategyRef": def.setElementNameStrategyRef(val); break;
                case "encoding": def.setEncoding(val); break;
                case "namespacePrefixRef": def.setNamespacePrefixRef(val); break;
                case "schema": def.setSchema(val); break;
                case "version": def.setVersion(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected SyslogDataFormat doParseSyslogDataFormat() throws IOException, XmlPullParserException {
        return doParse(new SyslogDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected TarFileDataFormat doParseTarFileDataFormat() throws IOException, XmlPullParserException {
        return doParse(new TarFileDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowEmptyDirectory": def.setAllowEmptyDirectory(java.lang.Boolean.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "preservePathElements": def.setPreservePathElements(java.lang.Boolean.valueOf(val)); break;
                case "usingIterator": def.setUsingIterator(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected ThriftDataFormat doParseThriftDataFormat() throws IOException, XmlPullParserException {
        return doParse(new ThriftDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeFormat": def.setContentTypeFormat(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "instanceClass": def.setInstanceClass(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected TidyMarkupDataFormat doParseTidyMarkupDataFormat() throws IOException, XmlPullParserException {
        return doParse(new TidyMarkupDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "dataObjectType": def.setDataObjectTypeName(val); break;
                case "omitXmlDeclaration": def.setOmitXmlDeclaration(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected UniVocityCsvDataFormat doParseUniVocityCsvDataFormat() throws IOException, XmlPullParserException {
        return doParse(new UniVocityCsvDataFormat(), (def, key, val) -> {
            switch (key) {
                case "asMap": def.setAsMap(java.lang.Boolean.valueOf(val)); break;
                case "comment": def.setComment(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "delimiter": def.setDelimiter(val); break;
                case "emptyValue": def.setEmptyValue(val); break;
                case "headerExtractionEnabled": def.setHeaderExtractionEnabled(java.lang.Boolean.valueOf(val)); break;
                case "headersDisabled": def.setHeadersDisabled(java.lang.Boolean.valueOf(val)); break;
                case "ignoreLeadingWhitespaces": def.setIgnoreLeadingWhitespaces(java.lang.Boolean.valueOf(val)); break;
                case "ignoreTrailingWhitespaces": def.setIgnoreTrailingWhitespaces(java.lang.Boolean.valueOf(val)); break;
                case "lazyLoad": def.setLazyLoad(java.lang.Boolean.valueOf(val)); break;
                case "lineSeparator": def.setLineSeparator(val); break;
                case "normalizedLineSeparator": def.setNormalizedLineSeparator(val); break;
                case "nullValue": def.setNullValue(val); break;
                case "numberOfRecordsToRead": def.setNumberOfRecordsToRead(java.lang.Integer.valueOf(val)); break;
                case "quote": def.setQuote(val); break;
                case "quoteAllFields": def.setQuoteAllFields(java.lang.Boolean.valueOf(val)); break;
                case "quoteEscape": def.setQuoteEscape(val); break;
                case "skipEmptyLines": def.setSkipEmptyLines(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "univocity-header": doAdd(doParseUniVocityHeader(), def.getHeaders(), def::setHeaders); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected UniVocityFixedWidthDataFormat doParseUniVocityFixedWidthDataFormat() throws IOException, XmlPullParserException {
        return doParse(new UniVocityFixedWidthDataFormat(), (def, key, val) -> {
            switch (key) {
                case "asMap": def.setAsMap(java.lang.Boolean.valueOf(val)); break;
                case "comment": def.setComment(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "emptyValue": def.setEmptyValue(val); break;
                case "headerExtractionEnabled": def.setHeaderExtractionEnabled(java.lang.Boolean.valueOf(val)); break;
                case "headersDisabled": def.setHeadersDisabled(java.lang.Boolean.valueOf(val)); break;
                case "ignoreLeadingWhitespaces": def.setIgnoreLeadingWhitespaces(java.lang.Boolean.valueOf(val)); break;
                case "ignoreTrailingWhitespaces": def.setIgnoreTrailingWhitespaces(java.lang.Boolean.valueOf(val)); break;
                case "lazyLoad": def.setLazyLoad(java.lang.Boolean.valueOf(val)); break;
                case "lineSeparator": def.setLineSeparator(val); break;
                case "normalizedLineSeparator": def.setNormalizedLineSeparator(val); break;
                case "nullValue": def.setNullValue(val); break;
                case "numberOfRecordsToRead": def.setNumberOfRecordsToRead(java.lang.Integer.valueOf(val)); break;
                case "padding": def.setPadding(val); break;
                case "recordEndsOnNewline": def.setRecordEndsOnNewline(java.lang.Boolean.valueOf(val)); break;
                case "skipEmptyLines": def.setSkipEmptyLines(java.lang.Boolean.valueOf(val)); break;
                case "skipTrailingCharsUntilNewline": def.setSkipTrailingCharsUntilNewline(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "univocity-header": doAdd(doParseUniVocityHeader(), def.getHeaders(), def::setHeaders); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected UniVocityHeader doParseUniVocityHeader() throws IOException, XmlPullParserException {
        return doParse(new UniVocityHeader(), (def, key, val) -> {
            switch (key) {
                case "length": def.setLength(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), (def, val) -> def.setName(val));
    }
    protected UniVocityTsvDataFormat doParseUniVocityTsvDataFormat() throws IOException, XmlPullParserException {
        return doParse(new UniVocityTsvDataFormat(), (def, key, val) -> {
            switch (key) {
                case "asMap": def.setAsMap(java.lang.Boolean.valueOf(val)); break;
                case "comment": def.setComment(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "emptyValue": def.setEmptyValue(val); break;
                case "escapeChar": def.setEscapeChar(val); break;
                case "headerExtractionEnabled": def.setHeaderExtractionEnabled(java.lang.Boolean.valueOf(val)); break;
                case "headersDisabled": def.setHeadersDisabled(java.lang.Boolean.valueOf(val)); break;
                case "ignoreLeadingWhitespaces": def.setIgnoreLeadingWhitespaces(java.lang.Boolean.valueOf(val)); break;
                case "ignoreTrailingWhitespaces": def.setIgnoreTrailingWhitespaces(java.lang.Boolean.valueOf(val)); break;
                case "lazyLoad": def.setLazyLoad(java.lang.Boolean.valueOf(val)); break;
                case "lineSeparator": def.setLineSeparator(val); break;
                case "normalizedLineSeparator": def.setNormalizedLineSeparator(val); break;
                case "nullValue": def.setNullValue(val); break;
                case "numberOfRecordsToRead": def.setNumberOfRecordsToRead(java.lang.Integer.valueOf(val)); break;
                case "skipEmptyLines": def.setSkipEmptyLines(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "univocity-header": doAdd(doParseUniVocityHeader(), def.getHeaders(), def::setHeaders); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XMLSecurityDataFormat doParseXMLSecurityDataFormat() throws IOException, XmlPullParserException {
        return doParse(new XMLSecurityDataFormat(), (def, key, val) -> {
            switch (key) {
                case "addKeyValueForEncryptedKey": def.setAddKeyValueForEncryptedKey(java.lang.Boolean.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "digestAlgorithm": def.setDigestAlgorithm(val); break;
                case "keyCipherAlgorithm": def.setKeyCipherAlgorithm(val); break;
                case "keyOrTrustStoreParametersRef": def.setKeyOrTrustStoreParametersRef(val); break;
                case "keyPassword": def.setKeyPassword(val); break;
                case "mgfAlgorithm": def.setMgfAlgorithm(val); break;
                case "passPhrase": def.setPassPhrase(val); break;
                case "passPhraseByte": def.setPassPhraseByte(asByteArray(val)); break;
                case "recipientKeyAlias": def.setRecipientKeyAlias(val); break;
                case "secureTag": def.setSecureTag(val); break;
                case "secureTagContents": def.setSecureTagContents(java.lang.Boolean.valueOf(val)); break;
                case "xmlCipherAlgorithm": def.setXmlCipherAlgorithm(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected XStreamDataFormat doParseXStreamDataFormat() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "driver": def.setDriver(val); break;
                case "driverRef": def.setDriverRef(val); break;
                case "encoding": def.setEncoding(val); break;
                case "mode": def.setMode(val); break;
                case "permissions": def.setPermissions(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "aliases": def.setAliases(unmarshal(new org.apache.camel.model.dataformat.XStreamDataFormat.AliasAdapter(), doParseAliasList())); break;
                case "converters": def.setConverters(unmarshal(new org.apache.camel.model.dataformat.XStreamDataFormat.ConvertersAdapter(), doParseConverterList())); break;
                case "implicitCollections": def.setImplicitCollections(unmarshal(new org.apache.camel.model.dataformat.XStreamDataFormat.ImplicitCollectionsAdapter(), doParseImplicitCollectionList())); break;
                case "omitFields": def.setOmitFields(unmarshal(new org.apache.camel.model.dataformat.XStreamDataFormat.OmitFieldsAdapter(), doParseOmitFieldList())); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XStreamDataFormat.ConverterList doParseConverterList() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.ConverterList(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "converter": doAdd(doParseConverterEntry(), def.getList(), def::setList); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XStreamDataFormat.AliasList doParseAliasList() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.AliasList(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "alias": doAdd(doParseAliasEntry(), def.getList(), def::setList); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XStreamDataFormat.OmitFieldList doParseOmitFieldList() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.OmitFieldList(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "omitField": doAdd(doParseOmitFieldEntry(), def.getList(), def::setList); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XStreamDataFormat.ImplicitCollectionList doParseImplicitCollectionList() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.ImplicitCollectionList(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "class": doAdd(doParseImplicitCollectionEntry(), def.getList(), def::setList); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XStreamDataFormat.AliasEntry doParseAliasEntry() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.AliasEntry(), (def, key, val) -> {
            switch (key) {
                case "class": def.setClsName(val); break;
                case "name": def.setName(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected XStreamDataFormat.ConverterEntry doParseConverterEntry() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.ConverterEntry(), (def, key, val) -> {
            switch (key) {
                case "class": def.setClsName(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected XStreamDataFormat.ImplicitCollectionEntry doParseImplicitCollectionEntry() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.ImplicitCollectionEntry(), (def, key, val) -> {
            switch (key) {
                case "name": def.setClsName(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "field": doAdd(doParseText(), def.getFields(), def::setFields); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XStreamDataFormat.OmitFieldEntry doParseOmitFieldEntry() throws IOException, XmlPullParserException {
        return doParse(new XStreamDataFormat.OmitFieldEntry(), (def, key, val) -> {
            switch (key) {
                case "class": def.setClsName(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "field": doAdd(doParseText(), def.getFields(), def::setFields); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected XmlRpcDataFormat doParseXmlRpcDataFormat() throws IOException, XmlPullParserException {
        return doParse(new XmlRpcDataFormat(), (def, key, val) -> {
            switch (key) {
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "request": def.setRequest(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected YAMLDataFormat doParseYAMLDataFormat() throws IOException, XmlPullParserException {
        return doParse(new YAMLDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowAnyType": def.setAllowAnyType(java.lang.Boolean.valueOf(val)); break;
                case "constructor": def.setConstructor(val); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "dumperOptions": def.setDumperOptions(val); break;
                case "library": def.setLibrary(org.apache.camel.model.dataformat.YAMLLibrary.valueOf(val)); break;
                case "prettyFlow": def.setPrettyFlow(java.lang.Boolean.valueOf(val)); break;
                case "representer": def.setRepresenter(val); break;
                case "resolver": def.setResolver(val); break;
                case "unmarshalTypeName": def.setUnmarshalTypeName(val); break;
                case "useApplicationContextClassLoader": def.setUseApplicationContextClassLoader(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "typeFilter": doAdd(doParseYAMLTypeFilterDefinition(), def.getTypeFilters(), def::setTypeFilters); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected YAMLTypeFilterDefinition doParseYAMLTypeFilterDefinition() throws IOException, XmlPullParserException {
        return doParse(new YAMLTypeFilterDefinition(), (def, key, val) -> {
            switch (key) {
                case "type": def.setType(org.apache.camel.model.dataformat.YAMLTypeFilterType.valueOf(val)); break;
                case "value": def.setValue(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected ZipDeflaterDataFormat doParseZipDeflaterDataFormat() throws IOException, XmlPullParserException {
        return doParse(new ZipDeflaterDataFormat(), (def, key, val) -> {
            switch (key) {
                case "compressionLevel": def.setCompressionLevel(java.lang.Integer.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected ZipFileDataFormat doParseZipFileDataFormat() throws IOException, XmlPullParserException {
        return doParse(new ZipFileDataFormat(), (def, key, val) -> {
            switch (key) {
                case "allowEmptyDirectory": def.setAllowEmptyDirectory(java.lang.Boolean.valueOf(val)); break;
                case "contentTypeHeader": def.setContentTypeHeader(val); break;
                case "preservePathElements": def.setPreservePathElements(java.lang.Boolean.valueOf(val)); break;
                case "usingIterator": def.setUsingIterator(java.lang.Boolean.valueOf(val)); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected ConstantExpression doParseConstantExpression() throws IOException, XmlPullParserException {
        return doParse(new ConstantExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected ExchangePropertyExpression doParseExchangePropertyExpression() throws IOException, XmlPullParserException {
        return doParse(new ExchangePropertyExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected GroovyExpression doParseGroovyExpression() throws IOException, XmlPullParserException {
        return doParse(new GroovyExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected HeaderExpression doParseHeaderExpression() throws IOException, XmlPullParserException {
        return doParse(new HeaderExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected Hl7TerserExpression doParseHl7TerserExpression() throws IOException, XmlPullParserException {
        return doParse(new Hl7TerserExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected JsonPathExpression doParseJsonPathExpression() throws IOException, XmlPullParserException {
        return doParse(new JsonPathExpression(), (def, key, val) -> {
            switch (key) {
                case "allowEasyPredicate": def.setAllowEasyPredicate(val); break;
                case "allowSimple": def.setAllowSimple(val); break;
                case "headerName": def.setHeaderName(val); break;
                case "resultType": def.setResultTypeName(val); break;
                case "suppressExceptions": def.setSuppressExceptions(val); break;
                case "writeAsString": def.setWriteAsString(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected LanguageExpression doParseLanguageExpression() throws IOException, XmlPullParserException {
        return doParse(new LanguageExpression(), (def, key, val) -> {
            switch (key) {
                case "language": def.setLanguage(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected MethodCallExpression doParseMethodCallExpression() throws IOException, XmlPullParserException {
        return doParse(new MethodCallExpression(), (def, key, val) -> {
            switch (key) {
                case "beanType": def.setBeanTypeName(val); break;
                case "method": def.setMethod(val); break;
                case "ref": def.setRef(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected MvelExpression doParseMvelExpression() throws IOException, XmlPullParserException {
        return doParse(new MvelExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected OgnlExpression doParseOgnlExpression() throws IOException, XmlPullParserException {
        return doParse(new OgnlExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected RefExpression doParseRefExpression() throws IOException, XmlPullParserException {
        return doParse(new RefExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected SimpleExpression doParseSimpleExpression() throws IOException, XmlPullParserException {
        return doParse(new SimpleExpression(), (def, key, val) -> {
            switch (key) {
                case "resultType": def.setResultTypeName(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected SpELExpression doParseSpELExpression() throws IOException, XmlPullParserException {
        return doParse(new SpELExpression(),
            expressionDefinitionAttributeHandler(), emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected TokenizerExpression doParseTokenizerExpression() throws IOException, XmlPullParserException {
        return doParse(new TokenizerExpression(), (def, key, val) -> {
            switch (key) {
                case "endToken": def.setEndToken(val); break;
                case "group": def.setGroup(val); break;
                case "groupDelimiter": def.setGroupDelimiter(val); break;
                case "headerName": def.setHeaderName(val); break;
                case "includeTokens": def.setIncludeTokens(val); break;
                case "inheritNamespaceTagName": def.setInheritNamespaceTagName(val); break;
                case "regex": def.setRegex(val); break;
                case "skipFirst": def.setSkipFirst(val); break;
                case "token": def.setToken(val); break;
                case "xml": def.setXml(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected XMLTokenizerExpression doParseXMLTokenizerExpression() throws IOException, XmlPullParserException {
        return doParse(new XMLTokenizerExpression(), (def, key, val) -> {
            switch (key) {
                case "group": def.setGroup(val); break;
                case "headerName": def.setHeaderName(val); break;
                case "mode": def.setMode(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected XPathExpression doParseXPathExpression() throws IOException, XmlPullParserException {
        return doParse(new XPathExpression(), (def, key, val) -> {
            switch (key) {
                case "documentType": def.setDocumentTypeName(val); break;
                case "factoryRef": def.setFactoryRef(val); break;
                case "headerName": def.setHeaderName(val); break;
                case "logNamespaces": def.setLogNamespaces(val); break;
                case "objectModel": def.setObjectModel(val); break;
                case "resultType": def.setResultTypeName(val); break;
                case "saxon": def.setSaxon(val); break;
                case "threadSafety": def.setThreadSafety(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected XQueryExpression doParseXQueryExpression() throws IOException, XmlPullParserException {
        return doParse(new XQueryExpression(), (def, key, val) -> {
            switch (key) {
                case "headerName": def.setHeaderName(val); break;
                case "type": def.setType(val); break;
                default: return expressionDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), expressionDefinitionValueHandler());
    }
    protected CustomLoadBalancerDefinition doParseCustomLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new CustomLoadBalancerDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected FailoverLoadBalancerDefinition doParseFailoverLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new FailoverLoadBalancerDefinition(), (def, key, val) -> {
            switch (key) {
                case "maximumFailoverAttempts": def.setMaximumFailoverAttempts(val); break;
                case "roundRobin": def.setRoundRobin(val); break;
                case "sticky": def.setSticky(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "exception": doAdd(doParseText(), def.getExceptions(), def::setExceptions); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected RandomLoadBalancerDefinition doParseRandomLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new RandomLoadBalancerDefinition(),
            identifiedTypeAttributeHandler(), emptyElementHandler(), noValueHandler());
    }
    protected RoundRobinLoadBalancerDefinition doParseRoundRobinLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new RoundRobinLoadBalancerDefinition(),
            identifiedTypeAttributeHandler(), emptyElementHandler(), noValueHandler());
    }
    protected StickyLoadBalancerDefinition doParseStickyLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new StickyLoadBalancerDefinition(),
            identifiedTypeAttributeHandler(), (def, key) -> {
            switch (key) {
                case "correlationExpression": def.setCorrelationExpression(doParseExpressionSubElementDefinition()); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected TopicLoadBalancerDefinition doParseTopicLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new TopicLoadBalancerDefinition(),
            identifiedTypeAttributeHandler(), emptyElementHandler(), noValueHandler());
    }
    protected WeightedLoadBalancerDefinition doParseWeightedLoadBalancerDefinition() throws IOException, XmlPullParserException {
        return doParse(new WeightedLoadBalancerDefinition(), (def, key, val) -> {
            switch (key) {
                case "distributionRatio": def.setDistributionRatio(val); break;
                case "distributionRatioDelimiter": def.setDistributionRatioDelimiter(val); break;
                case "roundRobin": def.setRoundRobin(val); break;
                default: return identifiedTypeAttributeHandler().accept(def, key, val);
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected DeleteVerbDefinition doParseDeleteVerbDefinition() throws IOException, XmlPullParserException {
        return doParse(new DeleteVerbDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "method": def.setMethod(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "routeId": def.setRouteId(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "param": doAdd(doParseRestOperationParamDefinition(), def.getParams(), def::setParams); break;
                case "responseMessage": doAdd(doParseRestOperationResponseMsgDefinition(), def.getResponseMsgs(), def::setResponseMsgs); break;
                case "security": doAdd(doParseSecurityDefinition(), def.getSecurity(), def::setSecurity); break;
                case "to": def.setToOrRoute(doParseToDefinition()); break;
                case "toD": def.setToOrRoute(doParseToDynamicDefinition()); break;
                case "route": def.setToOrRoute(doParseRouteDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected GetVerbDefinition doParseGetVerbDefinition() throws IOException, XmlPullParserException {
        return doParse(new GetVerbDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "method": def.setMethod(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "routeId": def.setRouteId(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "param": doAdd(doParseRestOperationParamDefinition(), def.getParams(), def::setParams); break;
                case "responseMessage": doAdd(doParseRestOperationResponseMsgDefinition(), def.getResponseMsgs(), def::setResponseMsgs); break;
                case "security": doAdd(doParseSecurityDefinition(), def.getSecurity(), def::setSecurity); break;
                case "to": def.setToOrRoute(doParseToDefinition()); break;
                case "toD": def.setToOrRoute(doParseToDynamicDefinition()); break;
                case "route": def.setToOrRoute(doParseRouteDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected HeadVerbDefinition doParseHeadVerbDefinition() throws IOException, XmlPullParserException {
        return doParse(new HeadVerbDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "method": def.setMethod(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "routeId": def.setRouteId(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "param": doAdd(doParseRestOperationParamDefinition(), def.getParams(), def::setParams); break;
                case "responseMessage": doAdd(doParseRestOperationResponseMsgDefinition(), def.getResponseMsgs(), def::setResponseMsgs); break;
                case "security": doAdd(doParseSecurityDefinition(), def.getSecurity(), def::setSecurity); break;
                case "to": def.setToOrRoute(doParseToDefinition()); break;
                case "toD": def.setToOrRoute(doParseToDynamicDefinition()); break;
                case "route": def.setToOrRoute(doParseRouteDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected PatchVerbDefinition doParsePatchVerbDefinition() throws IOException, XmlPullParserException {
        return doParse(new PatchVerbDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "method": def.setMethod(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "routeId": def.setRouteId(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "param": doAdd(doParseRestOperationParamDefinition(), def.getParams(), def::setParams); break;
                case "responseMessage": doAdd(doParseRestOperationResponseMsgDefinition(), def.getResponseMsgs(), def::setResponseMsgs); break;
                case "security": doAdd(doParseSecurityDefinition(), def.getSecurity(), def::setSecurity); break;
                case "to": def.setToOrRoute(doParseToDefinition()); break;
                case "toD": def.setToOrRoute(doParseToDynamicDefinition()); break;
                case "route": def.setToOrRoute(doParseRouteDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected PostVerbDefinition doParsePostVerbDefinition() throws IOException, XmlPullParserException {
        return doParse(new PostVerbDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "method": def.setMethod(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "routeId": def.setRouteId(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "param": doAdd(doParseRestOperationParamDefinition(), def.getParams(), def::setParams); break;
                case "responseMessage": doAdd(doParseRestOperationResponseMsgDefinition(), def.getResponseMsgs(), def::setResponseMsgs); break;
                case "security": doAdd(doParseSecurityDefinition(), def.getSecurity(), def::setSecurity); break;
                case "to": def.setToOrRoute(doParseToDefinition()); break;
                case "toD": def.setToOrRoute(doParseToDynamicDefinition()); break;
                case "route": def.setToOrRoute(doParseRouteDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected PutVerbDefinition doParsePutVerbDefinition() throws IOException, XmlPullParserException {
        return doParse(new PutVerbDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "method": def.setMethod(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "routeId": def.setRouteId(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "param": doAdd(doParseRestOperationParamDefinition(), def.getParams(), def::setParams); break;
                case "responseMessage": doAdd(doParseRestOperationResponseMsgDefinition(), def.getResponseMsgs(), def::setResponseMsgs); break;
                case "security": doAdd(doParseSecurityDefinition(), def.getSecurity(), def::setSecurity); break;
                case "to": def.setToOrRoute(doParseToDefinition()); break;
                case "toD": def.setToOrRoute(doParseToDynamicDefinition()); break;
                case "route": def.setToOrRoute(doParseRouteDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected RestConfigurationDefinition doParseRestConfigurationDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestConfigurationDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiComponent": def.setApiComponent(val); break;
                case "apiContextIdPattern": def.setApiContextIdPattern(val); break;
                case "apiContextListing": def.setApiContextListing(java.lang.Boolean.valueOf(val)); break;
                case "apiContextPath": def.setApiContextPath(val); break;
                case "apiContextRouteId": def.setApiContextRouteId(val); break;
                case "apiHost": def.setApiHost(val); break;
                case "apiVendorExtension": def.setApiVendorExtension(java.lang.Boolean.valueOf(val)); break;
                case "bindingMode": def.setBindingMode(org.apache.camel.model.rest.RestBindingMode.valueOf(val)); break;
                case "clientRequestValidation": def.setClientRequestValidation(java.lang.Boolean.valueOf(val)); break;
                case "component": def.setComponent(val); break;
                case "contextPath": def.setContextPath(val); break;
                case "enableCORS": def.setEnableCORS(java.lang.Boolean.valueOf(val)); break;
                case "host": def.setHost(val); break;
                case "hostNameResolver": def.setHostNameResolver(org.apache.camel.model.rest.RestHostNameResolver.valueOf(val)); break;
                case "jsonDataFormat": def.setJsonDataFormat(val); break;
                case "port": def.setPort(val); break;
                case "producerApiDoc": def.setProducerApiDoc(val); break;
                case "producerComponent": def.setProducerComponent(val); break;
                case "scheme": def.setScheme(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(java.lang.Boolean.valueOf(val)); break;
                case "useXForwardHeaders": def.setUseXForwardHeaders(java.lang.Boolean.valueOf(val)); break;
                case "xmlDataFormat": def.setXmlDataFormat(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "apiProperty": doAdd(doParseRestPropertyDefinition(), def.getApiProperties(), def::setApiProperties); break;
                case "componentProperty": doAdd(doParseRestPropertyDefinition(), def.getComponentProperties(), def::setComponentProperties); break;
                case "consumerProperty": doAdd(doParseRestPropertyDefinition(), def.getConsumerProperties(), def::setConsumerProperties); break;
                case "corsHeaders": doAdd(doParseRestPropertyDefinition(), def.getCorsHeaders(), def::setCorsHeaders); break;
                case "dataFormatProperty": doAdd(doParseRestPropertyDefinition(), def.getDataFormatProperties(), def::setDataFormatProperties); break;
                case "endpointProperty": doAdd(doParseRestPropertyDefinition(), def.getEndpointProperties(), def::setEndpointProperties); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected RestPropertyDefinition doParseRestPropertyDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestPropertyDefinition(), (def, key, val) -> {
            switch (key) {
                case "key": def.setKey(val); break;
                case "value": def.setValue(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected RestSecuritiesDefinition doParseRestSecuritiesDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestSecuritiesDefinition(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "apiKey": doAdd(doParseRestSecurityApiKey(), def.getSecurityDefinitions(), def::setSecurityDefinitions); break;
                case "basicAuth": doAdd(doParseRestSecurityBasicAuth(), def.getSecurityDefinitions(), def::setSecurityDefinitions); break;
                case "oauth2": doAdd(doParseRestSecurityOAuth2(), def.getSecurityDefinitions(), def::setSecurityDefinitions); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected VerbDefinition doParseVerbDefinition() throws IOException, XmlPullParserException {
        return doParse(new VerbDefinition(), (def, key, val) -> {
            switch (key) {
                case "apiDocs": def.setApiDocs(val); break;
                case "bindingMode": def.setBindingMode(val); break;
                case "clientRequestValidation": def.setClientRequestValidation(val); break;
                case "consumes": def.setConsumes(val); break;
                case "enableCORS": def.setEnableCORS(val); break;
                case "method": def.setMethod(val); break;
                case "outType": def.setOutType(val); break;
                case "produces": def.setProduces(val); break;
                case "routeId": def.setRouteId(val); break;
                case "skipBindingOnErrorCode": def.setSkipBindingOnErrorCode(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return optionalIdentifiedDefinitionAttributeHandler().accept(def, key, val);
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "param": doAdd(doParseRestOperationParamDefinition(), def.getParams(), def::setParams); break;
                case "responseMessage": doAdd(doParseRestOperationResponseMsgDefinition(), def.getResponseMsgs(), def::setResponseMsgs); break;
                case "security": doAdd(doParseSecurityDefinition(), def.getSecurity(), def::setSecurity); break;
                case "to": def.setToOrRoute(doParseToDefinition()); break;
                case "toD": def.setToOrRoute(doParseToDynamicDefinition()); break;
                case "route": def.setToOrRoute(doParseRouteDefinition()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected RestOperationParamDefinition doParseRestOperationParamDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestOperationParamDefinition(), (def, key, val) -> {
            switch (key) {
                case "arrayType": def.setArrayType(val); break;
                case "collectionFormat": def.setCollectionFormat(org.apache.camel.model.rest.CollectionFormat.valueOf(val)); break;
                case "dataFormat": def.setDataFormat(val); break;
                case "dataType": def.setDataType(val); break;
                case "defaultValue": def.setDefaultValue(val); break;
                case "description": def.setDescription(val); break;
                case "name": def.setName(val); break;
                case "required": def.setRequired(java.lang.Boolean.valueOf(val)); break;
                case "type": def.setType(org.apache.camel.model.rest.RestParamType.valueOf(val)); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "value": doAdd(doParseText(), def.getAllowableValues(), def::setAllowableValues); break;
                case "examples": doAdd(doParseRestPropertyDefinition(), def.getExamples(), def::setExamples); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected RestOperationResponseHeaderDefinition doParseRestOperationResponseHeaderDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestOperationResponseHeaderDefinition(), (def, key, val) -> {
            switch (key) {
                case "arrayType": def.setArrayType(val); break;
                case "collectionFormat": def.setCollectionFormat(org.apache.camel.model.rest.CollectionFormat.valueOf(val)); break;
                case "dataFormat": def.setDataFormat(val); break;
                case "dataType": def.setDataType(val); break;
                case "description": def.setDescription(val); break;
                case "example": def.setExample(val); break;
                case "name": def.setName(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "value": doAdd(doParseText(), def.getAllowableValues(), def::setAllowableValues); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected RestOperationResponseMsgDefinition doParseRestOperationResponseMsgDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestOperationResponseMsgDefinition(), (def, key, val) -> {
            switch (key) {
                case "code": def.setCode(val); break;
                case "message": def.setMessage(val); break;
                case "responseModel": def.setResponseModel(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "examples": doAdd(doParseRestPropertyDefinition(), def.getExamples(), def::setExamples); break;
                case "header": doAdd(doParseRestOperationResponseHeaderDefinition(), def.getHeaders(), def::setHeaders); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected RestSecurityApiKey doParseRestSecurityApiKey() throws IOException, XmlPullParserException {
        return doParse(new RestSecurityApiKey(), (def, key, val) -> {
            switch (key) {
                case "description": def.setDescription(val); break;
                case "inHeader": def.setInHeader(java.lang.Boolean.valueOf(val)); break;
                case "inQuery": def.setInQuery(java.lang.Boolean.valueOf(val)); break;
                case "key": def.setKey(val); break;
                case "name": def.setName(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected RestSecurityBasicAuth doParseRestSecurityBasicAuth() throws IOException, XmlPullParserException {
        return doParse(new RestSecurityBasicAuth(), (def, key, val) -> {
            switch (key) {
                case "description": def.setDescription(val); break;
                case "key": def.setKey(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected RestSecurityOAuth2 doParseRestSecurityOAuth2() throws IOException, XmlPullParserException {
        return doParse(new RestSecurityOAuth2(), (def, key, val) -> {
            switch (key) {
                case "authorizationUrl": def.setAuthorizationUrl(val); break;
                case "description": def.setDescription(val); break;
                case "flow": def.setFlow(val); break;
                case "key": def.setKey(val); break;
                case "tokenUrl": def.setTokenUrl(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "scopes": doAdd(doParseRestPropertyDefinition(), def.getScopes(), def::setScopes); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    public RestsDefinition parseRestsDefinition()
            throws IOException, XmlPullParserException {
        expectTag("rests");
        return doParseRestsDefinition();
    }
    protected RestsDefinition doParseRestsDefinition() throws IOException, XmlPullParserException {
        return doParse(new RestsDefinition(),
            optionalIdentifiedDefinitionAttributeHandler(), (def, key) -> {
            switch (key) {
                case "rest": doAdd(doParseRestDefinition(), def.getRests(), def::setRests); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        }, noValueHandler());
    }
    protected SecurityDefinition doParseSecurityDefinition() throws IOException, XmlPullParserException {
        return doParse(new SecurityDefinition(), (def, key, val) -> {
            switch (key) {
                case "key": def.setKey(val); break;
                case "scopes": def.setScopes(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected CustomTransformerDefinition doParseCustomTransformerDefinition() throws IOException, XmlPullParserException {
        return doParse(new CustomTransformerDefinition(), (def, key, val) -> {
            switch (key) {
                case "className": def.setClassName(val); break;
                case "fromType": def.setFromType(val); break;
                case "ref": def.setRef(val); break;
                case "scheme": def.setScheme(val); break;
                case "toType": def.setToType(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected DataFormatTransformerDefinition doParseDataFormatTransformerDefinition() throws IOException, XmlPullParserException {
        return doParse(new DataFormatTransformerDefinition(), (def, key, val) -> {
            switch (key) {
                case "fromType": def.setFromType(val); break;
                case "ref": def.setRef(val); break;
                case "scheme": def.setScheme(val); break;
                case "toType": def.setToType(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "any23": def.setDataFormatType(doParseAny23DataFormat()); break;
                case "asn1": def.setDataFormatType(doParseASN1DataFormat()); break;
                case "avro": def.setDataFormatType(doParseAvroDataFormat()); break;
                case "barcode": def.setDataFormatType(doParseBarcodeDataFormat()); break;
                case "base64": def.setDataFormatType(doParseBase64DataFormat()); break;
                case "beanio": def.setDataFormatType(doParseBeanioDataFormat()); break;
                case "bindy": def.setDataFormatType(doParseBindyDataFormat()); break;
                case "cbor": def.setDataFormatType(doParseCBORDataFormat()); break;
                case "crypto": def.setDataFormatType(doParseCryptoDataFormat()); break;
                case "csv": def.setDataFormatType(doParseCsvDataFormat()); break;
                case "custom": def.setDataFormatType(doParseCustomDataFormat()); break;
                case "fhirJson": def.setDataFormatType(doParseFhirJsonDataFormat()); break;
                case "fhirXml": def.setDataFormatType(doParseFhirXmlDataFormat()); break;
                case "flatpack": def.setDataFormatType(doParseFlatpackDataFormat()); break;
                case "grok": def.setDataFormatType(doParseGrokDataFormat()); break;
                case "gzip": def.setDataFormatType(doParseGzipDataFormat()); break;
                case "hl7": def.setDataFormatType(doParseHL7DataFormat()); break;
                case "ical": def.setDataFormatType(doParseIcalDataFormat()); break;
                case "jacksonxml": def.setDataFormatType(doParseJacksonXMLDataFormat()); break;
                case "jaxb": def.setDataFormatType(doParseJaxbDataFormat()); break;
                case "json": def.setDataFormatType(doParseJsonDataFormat()); break;
                case "jsonApi": def.setDataFormatType(doParseJsonApiDataFormat()); break;
                case "lzf": def.setDataFormatType(doParseLZFDataFormat()); break;
                case "mimeMultipart": def.setDataFormatType(doParseMimeMultipartDataFormat()); break;
                case "protobuf": def.setDataFormatType(doParseProtobufDataFormat()); break;
                case "rss": def.setDataFormatType(doParseRssDataFormat()); break;
                case "secureXML": def.setDataFormatType(doParseXMLSecurityDataFormat()); break;
                case "soapjaxb": def.setDataFormatType(doParseSoapJaxbDataFormat()); break;
                case "syslog": def.setDataFormatType(doParseSyslogDataFormat()); break;
                case "tarfile": def.setDataFormatType(doParseTarFileDataFormat()); break;
                case "thrift": def.setDataFormatType(doParseThriftDataFormat()); break;
                case "tidyMarkup": def.setDataFormatType(doParseTidyMarkupDataFormat()); break;
                case "univocity-csv": def.setDataFormatType(doParseUniVocityCsvDataFormat()); break;
                case "univocity-fixed": def.setDataFormatType(doParseUniVocityFixedWidthDataFormat()); break;
                case "univocity-tsv": def.setDataFormatType(doParseUniVocityTsvDataFormat()); break;
                case "xmlrpc": def.setDataFormatType(doParseXmlRpcDataFormat()); break;
                case "xstream": def.setDataFormatType(doParseXStreamDataFormat()); break;
                case "pgp": def.setDataFormatType(doParsePGPDataFormat()); break;
                case "yaml": def.setDataFormatType(doParseYAMLDataFormat()); break;
                case "zip": def.setDataFormatType(doParseZipDeflaterDataFormat()); break;
                case "zipfile": def.setDataFormatType(doParseZipFileDataFormat()); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected EndpointTransformerDefinition doParseEndpointTransformerDefinition() throws IOException, XmlPullParserException {
        return doParse(new EndpointTransformerDefinition(), (def, key, val) -> {
            switch (key) {
                case "fromType": def.setFromType(val); break;
                case "ref": def.setRef(val); break;
                case "scheme": def.setScheme(val); break;
                case "toType": def.setToType(val); break;
                case "uri": def.setUri(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected TransformersDefinition doParseTransformersDefinition() throws IOException, XmlPullParserException {
        return doParse(new TransformersDefinition(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "dataFormatTransformer": doAdd(doParseDataFormatTransformerDefinition(), def.getTransformers(), def::setTransformers); break;
                case "endpointTransformer": doAdd(doParseEndpointTransformerDefinition(), def.getTransformers(), def::setTransformers); break;
                case "customTransformer": doAdd(doParseCustomTransformerDefinition(), def.getTransformers(), def::setTransformers); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected CustomValidatorDefinition doParseCustomValidatorDefinition() throws IOException, XmlPullParserException {
        return doParse(new CustomValidatorDefinition(), (def, key, val) -> {
            switch (key) {
                case "className": def.setClassName(val); break;
                case "ref": def.setRef(val); break;
                case "type": def.setType(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected EndpointValidatorDefinition doParseEndpointValidatorDefinition() throws IOException, XmlPullParserException {
        return doParse(new EndpointValidatorDefinition(), (def, key, val) -> {
            switch (key) {
                case "ref": def.setRef(val); break;
                case "type": def.setType(val); break;
                case "uri": def.setUri(val); break;
                default: return false;
            }
            return true;
        }, emptyElementHandler(), noValueHandler());
    }
    protected PredicateValidatorDefinition doParsePredicateValidatorDefinition() throws IOException, XmlPullParserException {
        return doParse(new PredicateValidatorDefinition(), (def, key, val) -> {
            switch (key) {
                case "type": def.setType(val); break;
                default: return false;
            }
            return true;
        }, (def, key) -> {
            switch (key) {
                case "expressionDefinition": def.setExpression(doParseExpressionDefinition()); break;
                case "constant": def.setExpression(doParseConstantExpression()); break;
                case "exchangeProperty": def.setExpression(doParseExchangePropertyExpression()); break;
                case "groovy": def.setExpression(doParseGroovyExpression()); break;
                case "header": def.setExpression(doParseHeaderExpression()); break;
                case "hl7terser": def.setExpression(doParseHl7TerserExpression()); break;
                case "jsonpath": def.setExpression(doParseJsonPathExpression()); break;
                case "language": def.setExpression(doParseLanguageExpression()); break;
                case "method": def.setExpression(doParseMethodCallExpression()); break;
                case "mvel": def.setExpression(doParseMvelExpression()); break;
                case "ognl": def.setExpression(doParseOgnlExpression()); break;
                case "ref": def.setExpression(doParseRefExpression()); break;
                case "simple": def.setExpression(doParseSimpleExpression()); break;
                case "spel": def.setExpression(doParseSpELExpression()); break;
                case "tokenize": def.setExpression(doParseTokenizerExpression()); break;
                case "xtokenize": def.setExpression(doParseXMLTokenizerExpression()); break;
                case "xpath": def.setExpression(doParseXPathExpression()); break;
                case "xquery": def.setExpression(doParseXQueryExpression()); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected ValidatorsDefinition doParseValidatorsDefinition() throws IOException, XmlPullParserException {
        return doParse(new ValidatorsDefinition(),
            noAttributeHandler(), (def, key) -> {
            switch (key) {
                case "endpointValidator": doAdd(doParseEndpointValidatorDefinition(), def.getValidators(), def::setValidators); break;
                case "predicateValidator": doAdd(doParsePredicateValidatorDefinition(), def.getValidators(), def::setValidators); break;
                case "customValidator": doAdd(doParseCustomValidatorDefinition(), def.getValidators(), def::setValidators); break;
                default: return false;
            }
            return true;
        }, noValueHandler());
    }
    protected <T extends OutputExpressionNode> ElementHandler<T> outputExpressionNodeElementHandler() {
        return (def, key) ->  expressionNodeElementHandler().accept(def, key) || outputDefinitionElementHandler().accept(def, key);
    }
    protected <T extends ExpressionNode> ElementHandler<T> expressionNodeElementHandler() {
        return (def, key) -> {
            switch (key) {
                case "expressionDefinition": def.setExpression(doParseExpressionDefinition()); break;
                case "constant": def.setExpression(doParseConstantExpression()); break;
                case "exchangeProperty": def.setExpression(doParseExchangePropertyExpression()); break;
                case "groovy": def.setExpression(doParseGroovyExpression()); break;
                case "header": def.setExpression(doParseHeaderExpression()); break;
                case "hl7terser": def.setExpression(doParseHl7TerserExpression()); break;
                case "jsonpath": def.setExpression(doParseJsonPathExpression()); break;
                case "language": def.setExpression(doParseLanguageExpression()); break;
                case "method": def.setExpression(doParseMethodCallExpression()); break;
                case "mvel": def.setExpression(doParseMvelExpression()); break;
                case "ognl": def.setExpression(doParseOgnlExpression()); break;
                case "ref": def.setExpression(doParseRefExpression()); break;
                case "simple": def.setExpression(doParseSimpleExpression()); break;
                case "spel": def.setExpression(doParseSpELExpression()); break;
                case "tokenize": def.setExpression(doParseTokenizerExpression()); break;
                case "xtokenize": def.setExpression(doParseXMLTokenizerExpression()); break;
                case "xpath": def.setExpression(doParseXPathExpression()); break;
                case "xquery": def.setExpression(doParseXQueryExpression()); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            return true;
        };
    }
    protected <T extends ProcessorDefinition<?>> ElementHandler<T> outputDefinitionElementHandler() {
        return (def, key) -> {
            ProcessorDefinition<?> pd;
            switch (key) {
                case "aggregate": pd = doParseAggregateDefinition(); break;
                case "bean": pd = doParseBeanDefinition(); break;
                case "doCatch": pd = doParseCatchDefinition(); break;
                case "when": pd = doParseWhenDefinition(); break;
                case "choice": pd = doParseChoiceDefinition(); break;
                case "otherwise": pd = doParseOtherwiseDefinition(); break;
                case "circuitBreaker": pd = doParseCircuitBreakerDefinition(); break;
                case "claimCheck": pd = doParseClaimCheckDefinition(); break;
                case "convertBodyTo": pd = doParseConvertBodyDefinition(); break;
                case "delay": pd = doParseDelayDefinition(); break;
                case "dynamicRouter": pd = doParseDynamicRouterDefinition(); break;
                case "enrich": pd = doParseEnrichDefinition(); break;
                case "filter": pd = doParseFilterDefinition(); break;
                case "doFinally": pd = doParseFinallyDefinition(); break;
                case "idempotentConsumer": pd = doParseIdempotentConsumerDefinition(); break;
                case "inOnly": pd = doParseInOnlyDefinition(); break;
                case "inOut": pd = doParseInOutDefinition(); break;
                case "intercept": pd = doParseInterceptDefinition(); break;
                case "interceptFrom": pd = doParseInterceptFromDefinition(); break;
                case "interceptSendToEndpoint": pd = doParseInterceptSendToEndpointDefinition(); break;
                case "loadBalance": pd = doParseLoadBalanceDefinition(); break;
                case "log": pd = doParseLogDefinition(); break;
                case "loop": pd = doParseLoopDefinition(); break;
                case "marshal": pd = doParseMarshalDefinition(); break;
                case "multicast": pd = doParseMulticastDefinition(); break;
                case "onCompletion": pd = doParseOnCompletionDefinition(); break;
                case "onException": pd = doParseOnExceptionDefinition(); break;
                case "onFallback": pd = doParseOnFallbackDefinition(); break;
                case "pipeline": pd = doParsePipelineDefinition(); break;
                case "policy": pd = doParsePolicyDefinition(); break;
                case "pollEnrich": pd = doParsePollEnrichDefinition(); break;
                case "process": pd = doParseProcessDefinition(); break;
                case "recipientList": pd = doParseRecipientListDefinition(); break;
                case "removeHeader": pd = doParseRemoveHeaderDefinition(); break;
                case "removeHeaders": pd = doParseRemoveHeadersDefinition(); break;
                case "removeProperties": pd = doParseRemovePropertiesDefinition(); break;
                case "removeProperty": pd = doParseRemovePropertyDefinition(); break;
                case "resequence": pd = doParseResequenceDefinition(); break;
                case "rollback": pd = doParseRollbackDefinition(); break;
                case "route": pd = doParseRouteDefinition(); break;
                case "routingSlip": pd = doParseRoutingSlipDefinition(); break;
                case "saga": pd = doParseSagaDefinition(); break;
                case "sample": pd = doParseSamplingDefinition(); break;
                case "script": pd = doParseScriptDefinition(); break;
                case "setBody": pd = doParseSetBodyDefinition(); break;
                case "setExchangePattern": pd = doParseSetExchangePatternDefinition(); break;
                case "setHeader": pd = doParseSetHeaderDefinition(); break;
                case "setProperty": pd = doParseSetPropertyDefinition(); break;
                case "sort": pd = doParseSortDefinition(); break;
                case "split": pd = doParseSplitDefinition(); break;
                case "step": pd = doParseStepDefinition(); break;
                case "stop": pd = doParseStopDefinition(); break;
                case "threads": pd = doParseThreadsDefinition(); break;
                case "throttle": pd = doParseThrottleDefinition(); break;
                case "throwException": pd = doParseThrowExceptionDefinition(); break;
                case "to": pd = doParseToDefinition(); break;
                case "toD": pd = doParseToDynamicDefinition(); break;
                case "transacted": pd = doParseTransactedDefinition(); break;
                case "transform": pd = doParseTransformDefinition(); break;
                case "doTry": pd = doParseTryDefinition(); break;
                case "unmarshal": pd = doParseUnmarshalDefinition(); break;
                case "validate": pd = doParseValidateDefinition(); break;
                case "whenSkipSendToEndpoint": pd = doParseWhenSkipSendToEndpointDefinition(); break;
                case "wireTap": pd = doParseWireTapDefinition(); break;
                case "serviceCall": pd = doParseServiceCallDefinition(); break;
                default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);
            }
            def.getOutputs().add(pd);
            return true;
        };
    }
    protected <T extends OptionalIdentifiedDefinition<?>> ElementHandler<T> optionalIdentifiedDefinitionElementHandler() {
        return (def, name) -> {
            if ("description".equals(name)) {
                def.setDescription(doParseDescriptionDefinition());
                return true;
            } else {
                return false;
            }
        };
    }
    protected <T extends ServiceCallConfiguration> ElementHandler<T> serviceCallConfigurationElementHandler() {
        return (def, name) -> {
            if ("properties".equals(name)) {
                doAdd(doParsePropertyDefinition(), def.getProperties(), def::setProperties);
                return true;
            } else {
                return false;
            }
        };
    }
}