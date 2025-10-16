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
package org.apache.camel.component.flink;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.streaming.api.datastream.DataStream;

/**
 * Send DataSet jobs to an Apache Flink cluster.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "flink", title = "Flink", syntax = "flink:endpointType", producerOnly = true,
             category = { Category.TRANSFORMATION, Category.BIGDATA }, headersClass = FlinkConstants.class)
public class FlinkEndpoint extends DefaultEndpoint {

    @UriPath
    @Metadata(required = true)
    private EndpointType endpointType;
    @UriParam
    private DataSet dataSet;
    @UriParam
    private DataSetCallback dataSetCallback;
    @UriParam
    private DataStream dataStream;
    @UriParam
    private DataStreamCallback dataStreamCallback;
    @UriParam(defaultValue = "true")
    private boolean collect = true;
    @UriParam(label = "producer,advanced", enums = "STREAMING,BATCH,AUTOMATIC")
    private String executionMode;
    @UriParam(label = "producer,advanced")
    private Long checkpointInterval;
    @UriParam(label = "producer,advanced", enums = "EXACTLY_ONCE,AT_LEAST_ONCE")
    private String checkpointingMode;
    @UriParam(label = "producer,advanced")
    private Integer parallelism;
    @UriParam(label = "producer,advanced")
    private Integer maxParallelism;
    @UriParam(label = "producer,advanced")
    private String jobName;
    @UriParam(label = "producer,advanced")
    private Long checkpointTimeout;
    @UriParam(label = "producer,advanced")
    private Long minPauseBetweenCheckpoints;

    public FlinkEndpoint(String endpointUri, FlinkComponent component, EndpointType endpointType) {
        super(endpointUri, component);
        this.endpointType = endpointType;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (dataSet == null) {
            dataSet = getComponent().getDataSet();
        }

        if (dataSetCallback == null) {
            dataSetCallback = getComponent().getDataSetCallback();
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        if (endpointType == EndpointType.dataset) {
            return new DataSetFlinkProducer(this);
        } else if (endpointType == EndpointType.datastream) {
            return new DataStreamFlinkProducer(this);
        } else {
            return null;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Flink Component supports producer endpoints only.");
    }

    @Override
    public FlinkComponent getComponent() {
        return (FlinkComponent) super.getComponent();
    }

    /**
     * Type of the endpoint (dataset, datastream).
     */
    public void setEndpointType(EndpointType endpointType) {
        this.endpointType = endpointType;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public DataStream getDataStream() {
        return dataStream;
    }

    /**
     * DataSet to compute against.
     *
     * @deprecated The DataSet API is deprecated since Flink 1.12. Use the DataStream API with bounded streams instead.
     */
    @Deprecated(since = "4.16.0")
    public void setDataSet(DataSet ds) {
        this.dataSet = ds;
    }

    /**
     * DataStream to compute against.
     */
    public void setDataStream(DataStream ds) {
        this.dataStream = ds;
    }

    public DataSetCallback getDataSetCallback() {
        return dataSetCallback;
    }

    public DataStreamCallback getDataStreamCallback() {
        return dataStreamCallback;
    }

    /**
     * Function performing action against a DataSet.
     *
     * @deprecated The DataSet API is deprecated since Flink 1.12. Use the DataStream API with bounded streams instead.
     */
    @Deprecated(since = "4.16.0")
    public void setDataSetCallback(DataSetCallback dataSetCallback) {
        this.dataSetCallback = dataSetCallback;
    }

    /**
     * Function performing action against a DataStream.
     */
    public void setDataStreamCallback(DataStreamCallback dataStreamCallback) {
        this.dataStreamCallback = dataStreamCallback;
    }

    public boolean isCollect() {
        return collect;
    }

    /**
     * Indicates if results should be collected or counted.
     */
    public void setCollect(boolean collect) {
        this.collect = collect;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    /**
     * Execution mode for the Flink job. Options: STREAMING (default), BATCH, AUTOMATIC. BATCH mode is recommended for
     * bounded streams (batch processing).
     */
    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public Long getCheckpointInterval() {
        return checkpointInterval;
    }

    /**
     * Interval in milliseconds between checkpoints. Enables checkpointing when set. Recommended for streaming jobs to
     * ensure fault tolerance.
     */
    public void setCheckpointInterval(Long checkpointInterval) {
        this.checkpointInterval = checkpointInterval;
    }

    public String getCheckpointingMode() {
        return checkpointingMode;
    }

    /**
     * Checkpointing mode: EXACTLY_ONCE (default) or AT_LEAST_ONCE. EXACTLY_ONCE provides stronger guarantees but may
     * have higher overhead.
     */
    public void setCheckpointingMode(String checkpointingMode) {
        this.checkpointingMode = checkpointingMode;
    }

    public Integer getParallelism() {
        return parallelism;
    }

    /**
     * Parallelism for the Flink job. If not set, uses the default parallelism of the execution environment.
     */
    public void setParallelism(Integer parallelism) {
        this.parallelism = parallelism;
    }

    public Integer getMaxParallelism() {
        return maxParallelism;
    }

    /**
     * Maximum parallelism for the Flink job. Defines the upper bound for dynamic scaling and the number of key groups
     * for stateful operators.
     */
    public void setMaxParallelism(Integer maxParallelism) {
        this.maxParallelism = maxParallelism;
    }

    public String getJobName() {
        return jobName;
    }

    /**
     * Name for the Flink job. Useful for identification in the Flink UI and logs.
     */
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public Long getCheckpointTimeout() {
        return checkpointTimeout;
    }

    /**
     * Timeout in milliseconds for checkpoints. Checkpoints that take longer will be aborted.
     */
    public void setCheckpointTimeout(Long checkpointTimeout) {
        this.checkpointTimeout = checkpointTimeout;
    }

    public Long getMinPauseBetweenCheckpoints() {
        return minPauseBetweenCheckpoints;
    }

    /**
     * Minimum pause in milliseconds between consecutive checkpoints. Helps prevent checkpoint storms under heavy load.
     */
    public void setMinPauseBetweenCheckpoints(Long minPauseBetweenCheckpoints) {
        this.minPauseBetweenCheckpoints = minPauseBetweenCheckpoints;
    }
}
