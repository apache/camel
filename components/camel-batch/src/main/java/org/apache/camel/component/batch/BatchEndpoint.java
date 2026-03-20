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

import java.util.Map;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Process collections of items in structured batches with chunking, error thresholds, and watermark tracking.
 */
@UriEndpoint(firstVersion = "4.19.0", scheme = "batch", title = "Batch",
             syntax = "batch:jobName", producerOnly = true,
             remote = false, category = { Category.CORE, Category.SCHEDULING },
             headersClass = BatchConstants.class)
public class BatchEndpoint extends DefaultEndpoint {

    @UriPath(description = "The batch job name")
    @Metadata(required = true)
    private String jobName;

    @UriParam(defaultValue = "100", description = "Number of items per chunk")
    private int chunkSize = 100;

    @UriParam(defaultValue = "1.0", description = "Fraction of failed items (0.0-1.0) before aborting the batch."
                                                  + " 1.0 means never abort. 0.1 means abort after 10% failures.")
    private double errorThreshold = 1.0;

    @UriParam(description = "Bean reference to a Processor or endpoint URI that processes each individual item")
    @Metadata(required = true)
    private String processorRef;

    @UriParam(description = "Optional bean reference to an AggregationStrategy for collecting results")
    private String aggregationStrategy;

    @UriParam(description = "Optional bean reference to a Map<String, String> for watermark tracking")
    private String watermarkStore;

    @UriParam(description = "Key used in the watermark store. Defaults to the job name.")
    private String watermarkKey;

    @UriParam(defaultValue = "false", description = "Process chunks in parallel")
    private boolean parallelProcessing;

    public BatchEndpoint(String uri, BatchComponent component, String jobName) {
        super(uri, component);
        this.jobName = jobName;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BatchProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The batch component does not support consumers");
    }

    /**
     * Resolves the item processor. If processorRef starts with a component scheme (contains ':'), it is treated as an
     * endpoint URI. Otherwise, it is looked up as a bean reference.
     */
    public Processor resolveProcessor() {
        if (processorRef.contains(":")) {
            // treat as endpoint URI — send each item to this endpoint
            return exchange -> {
                getCamelContext().createProducerTemplate().send(processorRef, exchange);
            };
        }
        String name = processorRef.startsWith("#") ? processorRef.substring(1) : processorRef;
        return getCamelContext().getRegistry().lookupByNameAndType(name, Processor.class);
    }

    /**
     * Resolves the aggregation strategy bean if configured.
     */
    public AggregationStrategy resolveAggregationStrategy() {
        if (aggregationStrategy == null) {
            return null;
        }
        String name = aggregationStrategy.startsWith("#") ? aggregationStrategy.substring(1) : aggregationStrategy;
        return getCamelContext().getRegistry().lookupByNameAndType(name, AggregationStrategy.class);
    }

    /**
     * Resolves the watermark store bean if configured.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> resolveWatermarkStore() {
        if (watermarkStore == null) {
            return null;
        }
        String name = watermarkStore.startsWith("#") ? watermarkStore.substring(1) : watermarkStore;
        return getCamelContext().getRegistry().lookupByNameAndType(name, Map.class);
    }

    public String getEffectiveWatermarkKey() {
        return watermarkKey != null ? watermarkKey : jobName;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public double getErrorThreshold() {
        return errorThreshold;
    }

    public void setErrorThreshold(double errorThreshold) {
        this.errorThreshold = errorThreshold;
    }

    public String getProcessorRef() {
        return processorRef;
    }

    public void setProcessorRef(String processorRef) {
        this.processorRef = processorRef;
    }

    public String getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public String getWatermarkStore() {
        return watermarkStore;
    }

    public void setWatermarkStore(String watermarkStore) {
        this.watermarkStore = watermarkStore;
    }

    public String getWatermarkKey() {
        return watermarkKey;
    }

    public void setWatermarkKey(String watermarkKey) {
        this.watermarkKey = watermarkKey;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }
}
