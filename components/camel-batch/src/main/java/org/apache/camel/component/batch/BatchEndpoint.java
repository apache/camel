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
import org.apache.camel.support.EndpointHelper;

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

    @UriParam(defaultValue = "-1", description = "Maximum number of failed records before aborting the batch."
                                                 + " -1 means disabled (use errorThreshold instead). 0 means abort on first failure.")
    private int maxFailedRecords = -1;

    @UriParam(description = "Bean reference to a Processor or endpoint URI that processes each individual item."
                            + " Mutually exclusive with the steps option.")
    private String processorRef;

    @UriParam(description = "Comma-separated list of endpoint URIs or bean references for multi-step batch processing."
                            + " Items flow through each step sequentially. Use with acceptPolicy to filter items between steps."
                            + " Mutually exclusive with the processorRef option.")
    private String steps;

    @UriParam(defaultValue = "ALL", enums = "ALL,NO_FAILURES,FAILURES_ONLY",
              description = "Determines which items are eligible for processing in subsequent steps (after the first step)."
                            + " ALL processes all items, NO_FAILURES skips items that failed in a prior step,"
                            + " FAILURES_ONLY only processes items that failed (useful for error recovery steps).")
    private BatchAcceptPolicy acceptPolicy = BatchAcceptPolicy.ALL;

    @UriParam(description = "Optional bean reference to an AggregationStrategy for collecting results")
    private String aggregationStrategy;

    @UriParam(description = "Optional endpoint URI or bean reference called after the batch completes,"
                            + " regardless of success or failure. Receives the BatchResult as the exchange body.")
    private String onCompleteRef;

    @UriParam(description = "Optional bean reference to a Map<String, String> for watermark tracking")
    private String watermarkStore;

    @UriParam(description = "Key used in the watermark store. Defaults to the job name.")
    private String watermarkKey;

    @UriParam(description = "A Simple expression evaluated on each successfully processed item to extract a watermark value."
                            + " The last non-null value is stored in the watermark store. When set, index-based watermark skipping is disabled"
                            + " and the current watermark value is set as a CamelBatchWatermarkValue header instead.")
    private String watermarkExpression;

    @UriParam(defaultValue = "false", description = "Process items within each chunk in parallel")
    private boolean parallelProcessing;

    public BatchEndpoint(String uri, BatchComponent component, String jobName) {
        super(uri, component);
        this.jobName = jobName;
    }

    @Override
    public Producer createProducer() throws Exception {
        if (processorRef == null && steps == null) {
            throw new IllegalArgumentException("Either processorRef or steps must be configured");
        }
        if (processorRef != null && steps != null) {
            throw new IllegalArgumentException("Only one of processorRef or steps can be configured, not both");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0, was: " + chunkSize);
        }
        if (errorThreshold < 0.0 || errorThreshold > 1.0) {
            throw new IllegalArgumentException("errorThreshold must be between 0.0 and 1.0, was: " + errorThreshold);
        }
        if (watermarkExpression != null && watermarkStore == null) {
            throw new IllegalArgumentException(
                    "watermarkExpression is set but watermarkStore is not configured — watermark tracking will not be active");
        }
        return new BatchProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The batch component does not support consumers");
    }

    /**
     * Resolves the aggregation strategy bean if configured.
     */
    public AggregationStrategy resolveAggregationStrategy() {
        if (aggregationStrategy == null) {
            return null;
        }
        return EndpointHelper.resolveReferenceParameter(
                getCamelContext(), aggregationStrategy, AggregationStrategy.class, false);
    }

    /**
     * Resolves the watermark store bean if configured.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> resolveWatermarkStore() {
        if (watermarkStore == null) {
            return null;
        }
        return (Map<String, String>) EndpointHelper.resolveReferenceParameter(
                getCamelContext(), watermarkStore, Map.class, false);
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

    public int getMaxFailedRecords() {
        return maxFailedRecords;
    }

    public void setMaxFailedRecords(int maxFailedRecords) {
        this.maxFailedRecords = maxFailedRecords;
    }

    public String getProcessorRef() {
        return processorRef;
    }

    public void setProcessorRef(String processorRef) {
        this.processorRef = processorRef;
    }

    public String getSteps() {
        return steps;
    }

    public void setSteps(String steps) {
        this.steps = steps;
    }

    public BatchAcceptPolicy getAcceptPolicy() {
        return acceptPolicy;
    }

    public void setAcceptPolicy(BatchAcceptPolicy acceptPolicy) {
        this.acceptPolicy = acceptPolicy;
    }

    public String getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(String aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public String getOnCompleteRef() {
        return onCompleteRef;
    }

    public void setOnCompleteRef(String onCompleteRef) {
        this.onCompleteRef = onCompleteRef;
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

    public String getWatermarkExpression() {
        return watermarkExpression;
    }

    public void setWatermarkExpression(String watermarkExpression) {
        this.watermarkExpression = watermarkExpression;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }
}
