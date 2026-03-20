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
package org.apache.camel.component.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that processes collections of items in structured batches with chunking, error thresholds, multi-step
 * pipelines, and watermark tracking.
 */
public class BatchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(BatchProducer.class);

    private final BatchEndpoint endpoint;
    private ProducerTemplate producerTemplate;
    private List<Processor> stepProcessors;
    private AggregationStrategy aggregationStrategy;
    private Processor onCompleteProcessor;
    private Map<String, String> watermarkStore;
    private Expression watermarkExpr;
    private ExecutorService executorService;

    public BatchProducer(BatchEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        producerTemplate = endpoint.getCamelContext().createProducerTemplate();

        // Resolve step processors
        stepProcessors = resolveStepProcessors();
        if (stepProcessors.isEmpty()) {
            throw new IllegalArgumentException("No step processors resolved for batch job '" + endpoint.getJobName() + "'");
        }

        aggregationStrategy = endpoint.resolveAggregationStrategy();
        watermarkStore = endpoint.resolveWatermarkStore();

        // Resolve watermark expression
        if (endpoint.getWatermarkExpression() != null) {
            watermarkExpr = endpoint.getCamelContext()
                    .resolveLanguage("simple")
                    .createExpression(endpoint.getWatermarkExpression());
        }

        // Resolve onComplete
        if (endpoint.getOnCompleteRef() != null) {
            onCompleteProcessor = resolveRef(endpoint.getOnCompleteRef());
        }

        if (endpoint.isParallelProcessing()) {
            executorService = endpoint.getCamelContext().getExecutorServiceManager()
                    .newDefaultThreadPool(this, "BatchProducer-" + endpoint.getJobName());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (producerTemplate != null) {
            producerTemplate.stop();
            producerTemplate = null;
        }
        if (executorService != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String jobInstanceId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        // Set watermark header early so it's available during processing
        if (watermarkStore != null && watermarkExpr != null) {
            String wmKey = endpoint.getEffectiveWatermarkKey();
            String wmValue = watermarkStore.get(wmKey);
            exchange.getIn().setHeader(BatchConstants.BATCH_WATERMARK_VALUE, wmValue);
        }

        // Extract items from exchange body
        List<Object> items = extractItems(exchange);

        // Index-based watermark: skip already processed items
        if (watermarkStore != null && watermarkExpr == null) {
            items = applyWatermarkFilter(items);
        }

        int totalItems = items.size();
        if (totalItems == 0) {
            BatchResult result = new BatchResult(
                    endpoint.getJobName(), jobInstanceId, 0, 0, 0, 0, false, Collections.emptyList());
            exchange.getIn().setBody(result);
            setResultHeaders(exchange, result, jobInstanceId);
            fireOnComplete(exchange, result);
            return;
        }

        LOG.debug("Batch job '{}' [{}]: processing {} items through {} step(s) in chunks of {}",
                endpoint.getJobName(), jobInstanceId, totalItems,
                stepProcessors.size(), endpoint.getChunkSize());

        // Initialize per-item exchanges (persist across steps)
        Exchange[] itemExchanges = new Exchange[totalItems];
        for (int i = 0; i < totalItems; i++) {
            itemExchanges[i] = createItemExchange(exchange, items.get(i), i, totalItems);
            itemExchanges[i].getIn().setHeader(BatchConstants.BATCH_JOB_INSTANCE_ID, jobInstanceId);
        }

        // Track failures across steps
        // Key: item index, Value: most recent failure for that item
        Map<Integer, BatchResult.BatchFailure> failureMap = new ConcurrentHashMap<>();
        boolean aborted = false;

        // Process through steps
        for (int stepIndex = 0; stepIndex < stepProcessors.size() && !aborted; stepIndex++) {
            Processor stepProcessor = stepProcessors.get(stepIndex);

            // First step always processes ALL; subsequent steps use acceptPolicy
            BatchAcceptPolicy policy = stepIndex == 0 ? BatchAcceptPolicy.ALL : endpoint.getAcceptPolicy();
            List<Integer> eligible = getEligibleItems(totalItems, failureMap.keySet(), policy);

            if (eligible.isEmpty()) {
                continue;
            }

            // Split eligible indices into chunks
            List<List<Integer>> chunks = splitIntoChunks(eligible, endpoint.getChunkSize());

            for (int chunkIndex = 0; chunkIndex < chunks.size() && !aborted; chunkIndex++) {
                List<Integer> chunk = chunks.get(chunkIndex);

                if (endpoint.isParallelProcessing()) {
                    processChunkParallel(
                            items, itemExchanges, chunk, stepIndex, chunkIndex, stepProcessor, failureMap);
                    aborted = isThresholdExceeded(failureMap.size(), totalItems);
                } else {
                    aborted = processChunkSequential(
                            items, itemExchanges, chunk, stepIndex, chunkIndex, stepProcessor, failureMap, totalItems);
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        int failureCount = failureMap.size();
        int successCount = totalItems - failureCount;

        // Update watermark
        updateWatermark(items, itemExchanges, failureMap, totalItems);

        // Build result
        List<BatchResult.BatchFailure> finalFailures = new ArrayList<>(failureMap.values());
        BatchResult result = new BatchResult(
                endpoint.getJobName(), jobInstanceId, totalItems,
                successCount, failureCount, duration, aborted, finalFailures);

        // Set result on exchange
        if (aggregationStrategy != null) {
            Exchange aggregated = null;
            for (int i = 0; i < totalItems; i++) {
                if (!failureMap.containsKey(i)) {
                    aggregated = aggregationStrategy.aggregate(aggregated, itemExchanges[i]);
                }
            }
            if (aggregated != null) {
                exchange.getIn().setBody(aggregated.getIn().getBody());
            } else {
                exchange.getIn().setBody(result);
            }
        } else {
            exchange.getIn().setBody(result);
        }

        setResultHeaders(exchange, result, jobInstanceId);

        LOG.debug("Batch job '{}' [{}] completed: {}", endpoint.getJobName(), jobInstanceId, result);

        // On Complete fires before potential exception
        fireOnComplete(exchange, result);

        if (aborted) {
            throw new BatchException(
                    "Batch job '" + endpoint.getJobName() + "' aborted: "
                                     + failureCount + "/" + totalItems + " failures exceeded threshold",
                    result);
        }
    }

    private boolean processChunkSequential(
            List<Object> items, Exchange[] itemExchanges, List<Integer> chunk,
            int stepIndex, int chunkIndex, Processor stepProcessor,
            Map<Integer, BatchResult.BatchFailure> failureMap, int totalItems) {

        for (int idx : chunk) {
            Exchange itemExchange = itemExchanges[idx];
            itemExchange.getIn().setHeader(BatchConstants.BATCH_STEP_INDEX, stepIndex);
            itemExchange.getIn().setHeader(BatchConstants.BATCH_CHUNK_INDEX, chunkIndex);

            try {
                stepProcessor.process(itemExchange);

                if (itemExchange.getException() != null) {
                    Exception ex = itemExchange.getException();
                    itemExchange.setException(null); // clear for potential recovery steps
                    throw ex;
                }

                // Success — remove from failures if recovering from a prior step
                failureMap.remove(idx);
            } catch (Exception e) {
                failureMap.put(idx, new BatchResult.BatchFailure(idx, items.get(idx), e, stepIndex));
                LOG.debug("Batch job '{}': item {} failed at step {}: {}",
                        endpoint.getJobName(), idx, stepIndex, e.getMessage());

                if (isThresholdExceeded(failureMap.size(), totalItems)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void processChunkParallel(
            List<Object> items, Exchange[] itemExchanges, List<Integer> chunk,
            int stepIndex, int chunkIndex, Processor stepProcessor,
            Map<Integer, BatchResult.BatchFailure> failureMap) {

        List<Future<?>> futures = new ArrayList<>();

        for (int idx : chunk) {
            final int itemIdx = idx;
            futures.add(executorService.submit(() -> {
                Exchange itemExchange = itemExchanges[itemIdx];
                itemExchange.getIn().setHeader(BatchConstants.BATCH_STEP_INDEX, stepIndex);
                itemExchange.getIn().setHeader(BatchConstants.BATCH_CHUNK_INDEX, chunkIndex);

                try {
                    stepProcessor.process(itemExchange);

                    if (itemExchange.getException() != null) {
                        Exception ex = itemExchange.getException();
                        itemExchange.setException(null);
                        throw ex;
                    }

                    failureMap.remove(itemIdx);
                } catch (Exception e) {
                    failureMap.put(itemIdx, new BatchResult.BatchFailure(itemIdx, items.get(itemIdx), e, stepIndex));
                    LOG.debug("Batch job '{}': item {} failed at step {}: {}",
                            endpoint.getJobName(), itemIdx, stepIndex, e.getMessage());
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // already handled inside the task
            }
        }
    }

    private Exchange createItemExchange(Exchange parent, Object item, int index, int totalSize) {
        Exchange itemExchange = ExchangeHelper.createCorrelatedCopy(parent, false);
        itemExchange.getIn().setBody(item);
        itemExchange.getIn().setHeader(BatchConstants.BATCH_JOB_NAME, endpoint.getJobName());
        itemExchange.getIn().setHeader(BatchConstants.BATCH_INDEX, index);
        itemExchange.getIn().setHeader(BatchConstants.BATCH_SIZE, totalSize);
        return itemExchange;
    }

    private void setResultHeaders(Exchange exchange, BatchResult result, String jobInstanceId) {
        exchange.getIn().setHeader(BatchConstants.BATCH_JOB_NAME, endpoint.getJobName());
        exchange.getIn().setHeader(BatchConstants.BATCH_JOB_INSTANCE_ID, jobInstanceId);
        exchange.getIn().setHeader(BatchConstants.BATCH_TOTAL, result.getTotalItems());
        exchange.getIn().setHeader(BatchConstants.BATCH_SUCCESS, result.getSuccessCount());
        exchange.getIn().setHeader(BatchConstants.BATCH_FAILED, result.getFailureCount());
        exchange.getIn().setHeader(BatchConstants.BATCH_DURATION, result.getDuration());
        exchange.getIn().setHeader(BatchConstants.BATCH_ABORTED, result.isAborted());
    }

    private void fireOnComplete(Exchange exchange, BatchResult result) {
        if (onCompleteProcessor == null) {
            return;
        }
        try {
            Exchange completeExchange = exchange.copy();
            completeExchange.getIn().setBody(result);
            onCompleteProcessor.process(completeExchange);
        } catch (Exception e) {
            LOG.warn("Batch job '{}': onComplete callback failed: {}", endpoint.getJobName(), e.getMessage(), e);
        }
    }

    private List<Processor> resolveStepProcessors() {
        List<Processor> processors = new ArrayList<>();

        if (endpoint.getSteps() != null) {
            String[] stepRefs = endpoint.getSteps().split(",");
            for (String ref : stepRefs) {
                String trimmed = ref.trim();
                if (!trimmed.isEmpty()) {
                    processors.add(resolveRef(trimmed));
                }
            }
        } else if (endpoint.getProcessorRef() != null) {
            processors.add(resolveRef(endpoint.getProcessorRef()));
        }

        return processors;
    }

    private Processor resolveRef(String ref) {
        if (ref.contains(":")) {
            // Endpoint URI — send via shared ProducerTemplate
            return exchange -> producerTemplate.send(ref, exchange);
        }
        return EndpointHelper.resolveReferenceParameter(
                endpoint.getCamelContext(), ref, Processor.class);
    }

    private List<Integer> getEligibleItems(int totalItems, Set<Integer> failedIndices, BatchAcceptPolicy policy) {
        List<Integer> eligible = new ArrayList<>();
        for (int i = 0; i < totalItems; i++) {
            boolean isFailed = failedIndices.contains(i);
            switch (policy) {
                case ALL:
                    eligible.add(i);
                    break;
                case NO_FAILURES:
                    if (!isFailed) {
                        eligible.add(i);
                    }
                    break;
                case FAILURES_ONLY:
                    if (isFailed) {
                        eligible.add(i);
                    }
                    break;
            }
        }
        return eligible;
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractItems(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body instanceof List) {
            return new ArrayList<>((List<Object>) body);
        }
        if (body instanceof Iterator) {
            List<Object> items = new ArrayList<>();
            ((Iterator<Object>) body).forEachRemaining(items::add);
            return items;
        }
        if (body instanceof Iterable) {
            List<Object> items = new ArrayList<>();
            ((Iterable<Object>) body).forEach(items::add);
            return items;
        }
        // Try type conversion to Iterator
        Iterator<?> iterator = exchange.getContext().getTypeConverter().tryConvertTo(Iterator.class, exchange, body);
        if (iterator != null) {
            List<Object> items = new ArrayList<>();
            iterator.forEachRemaining(items::add);
            return items;
        }
        // Single item
        List<Object> items = new ArrayList<>();
        items.add(body);
        return items;
    }

    private List<Object> applyWatermarkFilter(List<Object> items) {
        if (watermarkStore == null) {
            return items;
        }
        String key = endpoint.getEffectiveWatermarkKey();
        String watermarkValue = watermarkStore.get(key);
        if (watermarkValue == null) {
            return items;
        }
        int lastProcessed = Integer.parseInt(watermarkValue);
        if (lastProcessed >= items.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(items.subList(lastProcessed, items.size()));
    }

    private void updateWatermark(
            List<Object> items, Exchange[] itemExchanges,
            Map<Integer, BatchResult.BatchFailure> failureMap, int totalItems) {
        if (watermarkStore == null) {
            return;
        }

        if (watermarkExpr != null) {
            // Value-based: evaluate expression on the last successful item
            String lastValue = null;
            for (int i = totalItems - 1; i >= 0; i--) {
                if (!failureMap.containsKey(i)) {
                    lastValue = watermarkExpr.evaluate(itemExchanges[i], String.class);
                    if (lastValue != null) {
                        break;
                    }
                }
            }
            if (lastValue != null) {
                watermarkStore.put(endpoint.getEffectiveWatermarkKey(), lastValue);
            }
        } else {
            // Index-based: track total processed count
            String key = endpoint.getEffectiveWatermarkKey();
            String existing = watermarkStore.get(key);
            int previous = existing != null ? Integer.parseInt(existing) : 0;
            watermarkStore.put(key, String.valueOf(previous + totalItems));
        }
    }

    private boolean isThresholdExceeded(int failures, int total) {
        // Check absolute count
        int maxFailed = endpoint.getMaxFailedRecords();
        if (maxFailed >= 0 && failures > maxFailed) {
            return true;
        }
        // Check percentage threshold
        if (total == 0 || endpoint.getErrorThreshold() >= 1.0) {
            return false;
        }
        double ratio = (double) failures / total;
        return ratio > endpoint.getErrorThreshold();
    }

    private <T> List<List<T>> splitIntoChunks(List<T> items, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += chunkSize) {
            chunks.add(items.subList(i, Math.min(i + chunkSize, items.size())));
        }
        return chunks;
    }
}
