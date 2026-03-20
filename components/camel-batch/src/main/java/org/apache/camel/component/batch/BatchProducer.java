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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that processes collections of items in structured batches with chunking, error thresholds, and watermark
 * tracking.
 */
public class BatchProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(BatchProducer.class);

    private final BatchEndpoint endpoint;
    private Processor itemProcessor;
    private AggregationStrategy aggregationStrategy;
    private Map<String, String> watermarkStore;
    private ExecutorService executorService;

    public BatchProducer(BatchEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        itemProcessor = endpoint.resolveProcessor();
        ObjectHelper.notNull(itemProcessor, "processorRef", endpoint);

        aggregationStrategy = endpoint.resolveAggregationStrategy();
        watermarkStore = endpoint.resolveWatermarkStore();

        if (endpoint.isParallelProcessing()) {
            executorService = endpoint.getCamelContext().getExecutorServiceManager()
                    .newDefaultThreadPool(this, "BatchProducer-" + endpoint.getJobName());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (executorService != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        long startTime = System.currentTimeMillis();

        // Extract items from exchange body
        List<Object> items = extractItems(exchange);
        int totalItems = items.size();

        // Apply watermark filtering
        items = applyWatermarkFilter(items);

        LOG.debug("Batch job '{}': processing {} items (after watermark filter) in chunks of {}",
                endpoint.getJobName(), items.size(), endpoint.getChunkSize());

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        List<BatchResult.BatchFailure> failures = new ArrayList<>();
        boolean aborted = false;
        int processedTotal = items.size();

        // Split into chunks and process
        List<List<Object>> chunks = splitIntoChunks(items, endpoint.getChunkSize());
        Exchange aggregated = null;

        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            List<Object> chunk = chunks.get(chunkIndex);

            if (endpoint.isParallelProcessing()) {
                aborted = processChunkParallel(
                        exchange, chunk, chunkIndex, items, successCount, failureCount, failures);
            } else {
                aborted = processChunkSequential(
                        exchange, chunk, chunkIndex, items, successCount, failureCount, failures);
            }

            // Apply aggregation strategy for the chunk
            if (aggregationStrategy != null) {
                for (Object item : chunk) {
                    Exchange itemExchange = createItemExchange(exchange, item, 0, items.size(), chunkIndex);
                    aggregated = aggregationStrategy.aggregate(aggregated, itemExchange);
                }
            }

            if (aborted) {
                break;
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Update watermark
        updateWatermark(processedTotal);

        // Build result
        BatchResult result = new BatchResult(
                endpoint.getJobName(), processedTotal,
                successCount.get(), failureCount.get(),
                duration, aborted, failures);

        // Set result on exchange
        if (aggregated != null && aggregationStrategy != null) {
            exchange.getIn().setBody(aggregated.getIn().getBody());
        } else {
            exchange.getIn().setBody(result);
        }

        // Set headers
        exchange.getIn().setHeader(BatchConstants.BATCH_JOB_NAME, endpoint.getJobName());
        exchange.getIn().setHeader(BatchConstants.BATCH_TOTAL, processedTotal);
        exchange.getIn().setHeader(BatchConstants.BATCH_SUCCESS, successCount.get());
        exchange.getIn().setHeader(BatchConstants.BATCH_FAILED, failureCount.get());
        exchange.getIn().setHeader(BatchConstants.BATCH_DURATION, duration);
        exchange.getIn().setHeader(BatchConstants.BATCH_ABORTED, aborted);

        LOG.info("Batch job '{}' completed: {}", endpoint.getJobName(), result);

        if (aborted) {
            throw new BatchException(
                    "Batch job '" + endpoint.getJobName() + "' aborted: error threshold "
                                     + endpoint.getErrorThreshold() + " exceeded with "
                                     + failureCount.get() + "/" + processedTotal + " failures",
                    result);
        }
    }

    private boolean processChunkSequential(
            Exchange exchange, List<Object> chunk, int chunkIndex,
            List<Object> allItems, AtomicInteger successCount,
            AtomicInteger failureCount, List<BatchResult.BatchFailure> failures) {

        int baseIndex = chunkIndex * endpoint.getChunkSize();
        for (int i = 0; i < chunk.size(); i++) {
            int itemIndex = baseIndex + i;
            Object item = chunk.get(i);
            try {
                Exchange itemExchange = createItemExchange(exchange, item, itemIndex, allItems.size(), chunkIndex);
                itemProcessor.process(itemExchange);

                if (itemExchange.getException() != null) {
                    throw itemExchange.getException();
                }

                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                failures.add(new BatchResult.BatchFailure(itemIndex, item, e));
                LOG.debug("Batch job '{}': item {} failed: {}", endpoint.getJobName(), itemIndex, e.getMessage());

                if (isThresholdExceeded(failureCount.get(), successCount.get() + failureCount.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean processChunkParallel(
            Exchange exchange, List<Object> chunk, int chunkIndex,
            List<Object> allItems, AtomicInteger successCount,
            AtomicInteger failureCount, List<BatchResult.BatchFailure> failures) {

        int baseIndex = chunkIndex * endpoint.getChunkSize();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < chunk.size(); i++) {
            int itemIndex = baseIndex + i;
            Object item = chunk.get(i);
            futures.add(executorService.submit(() -> {
                try {
                    Exchange itemExchange = createItemExchange(exchange, item, itemIndex, allItems.size(), chunkIndex);
                    itemProcessor.process(itemExchange);

                    if (itemExchange.getException() != null) {
                        throw itemExchange.getException();
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    synchronized (failures) {
                        failures.add(new BatchResult.BatchFailure(itemIndex, item, e));
                    }
                    LOG.debug("Batch job '{}': item {} failed: {}", endpoint.getJobName(), itemIndex, e.getMessage());
                }
            }));
        }

        // Wait for all futures
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // already handled inside the task
            }
        }

        return isThresholdExceeded(failureCount.get(), successCount.get() + failureCount.get());
    }

    private Exchange createItemExchange(Exchange parent, Object item, int index, int totalSize, int chunkIndex) {
        Exchange itemExchange = parent.copy();
        itemExchange.getIn().setBody(item);
        itemExchange.getIn().setHeader(BatchConstants.BATCH_JOB_NAME, endpoint.getJobName());
        itemExchange.getIn().setHeader(BatchConstants.BATCH_INDEX, index);
        itemExchange.getIn().setHeader(BatchConstants.BATCH_SIZE, totalSize);
        itemExchange.getIn().setHeader(BatchConstants.BATCH_CHUNK_INDEX, chunkIndex);
        return itemExchange;
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

    private void updateWatermark(int processedCount) {
        if (watermarkStore == null) {
            return;
        }
        String key = endpoint.getEffectiveWatermarkKey();
        String existing = watermarkStore.get(key);
        int previous = existing != null ? Integer.parseInt(existing) : 0;
        watermarkStore.put(key, String.valueOf(previous + processedCount));
    }

    private boolean isThresholdExceeded(int failures, int total) {
        if (total == 0 || endpoint.getErrorThreshold() >= 1.0) {
            return false;
        }
        double ratio = (double) failures / total;
        return ratio > endpoint.getErrorThreshold();
    }

    private List<List<Object>> splitIntoChunks(List<Object> items, int chunkSize) {
        List<List<Object>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += chunkSize) {
            chunks.add(items.subList(i, Math.min(i + chunkSize, items.size())));
        }
        return chunks;
    }
}
