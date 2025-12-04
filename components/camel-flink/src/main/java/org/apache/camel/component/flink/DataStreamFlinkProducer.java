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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer for executing Flink DataStream operations with support for modern Flink features including execution mode
 * configuration, checkpointing, and parallelism settings.
 */
public class DataStreamFlinkProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DataStreamFlinkProducer.class);

    private volatile boolean environmentConfigured = false;

    public DataStreamFlinkProducer(FlinkEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        DataStream ds = resolveDataStream(exchange);

        // Configure environment on first use when DataStream is available
        if (!environmentConfigured && ds != null) {
            synchronized (this) {
                if (!environmentConfigured) {
                    configureStreamExecutionEnvironment(ds);
                    environmentConfigured = true;
                }
            }
        }

        DataStreamCallback dataStreamCallback = resolveDataStreamCallback(exchange);
        Object body = exchange.getIn().getBody();
        Object result = body instanceof List
                ? dataStreamCallback.onDataStream(ds, ((List) body).toArray(new Object[0]))
                : dataStreamCallback.onDataStream(ds, body);
        collectResults(exchange, result);
    }

    @Override
    public FlinkEndpoint getEndpoint() {
        return (FlinkEndpoint) super.getEndpoint();
    }

    protected void collectResults(Exchange exchange, Object result) {
        if (result instanceof DataStream) {
            if (getEndpoint().isCollect()) {
                throw new IllegalArgumentException("collect mode not supported for Flink DataStreams.");
            } else {
                exchange.getIn().setBody(result);
                exchange.getIn().setHeader(FlinkConstants.FLINK_DATASTREAM_HEADER, result);
            }
        } else {
            exchange.getIn().setBody(result);
        }
    }

    protected DataStream resolveDataStream(Exchange exchange) {
        if (exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_HEADER) != null) {
            return (DataStream) exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_HEADER);
        } else if (getEndpoint().getDataStream() != null) {
            return getEndpoint().getDataStream();
        } else {
            throw new IllegalArgumentException("No DataStream defined");
        }
    }

    protected DataStreamCallback resolveDataStreamCallback(Exchange exchange) {
        if (exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER) != null) {
            return (DataStreamCallback) exchange.getIn().getHeader(FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER);
        } else if (getEndpoint().getDataStreamCallback() != null) {
            return getEndpoint().getDataStreamCallback();
        } else {
            throw new IllegalArgumentException("Cannot resolve DataStream callback.");
        }
    }

    /**
     * Configures the StreamExecutionEnvironment with the settings from the endpoint. This includes execution mode,
     * checkpointing, parallelism, and other advanced options.
     *
     * @param dataStream the DataStream to configure the environment for
     */
    protected void configureStreamExecutionEnvironment(DataStream dataStream) {
        if (dataStream == null) {
            LOG.debug("No DataStream provided, skipping environment configuration");
            return;
        }

        StreamExecutionEnvironment env = dataStream.getExecutionEnvironment();

        // Configure execution mode (BATCH, STREAMING, AUTOMATIC)
        if (getEndpoint().getExecutionMode() != null) {
            try {
                RuntimeExecutionMode mode =
                        RuntimeExecutionMode.valueOf(getEndpoint().getExecutionMode());
                env.setRuntimeMode(mode);
                LOG.info("Set Flink runtime execution mode to: {}", mode);
            } catch (IllegalArgumentException e) {
                LOG.warn(
                        "Invalid execution mode '{}'. Valid values are: STREAMING, BATCH, AUTOMATIC",
                        getEndpoint().getExecutionMode());
            }
        }

        // Configure parallelism
        if (getEndpoint().getParallelism() != null) {
            env.setParallelism(getEndpoint().getParallelism());
            LOG.info("Set Flink parallelism to: {}", getEndpoint().getParallelism());
        }

        // Configure max parallelism
        if (getEndpoint().getMaxParallelism() != null) {
            env.setMaxParallelism(getEndpoint().getMaxParallelism());
            LOG.info("Set Flink max parallelism to: {}", getEndpoint().getMaxParallelism());
        }

        // Configure checkpointing
        if (getEndpoint().getCheckpointInterval() != null && getEndpoint().getCheckpointInterval() > 0) {
            env.enableCheckpointing(getEndpoint().getCheckpointInterval());
            LOG.info("Enabled checkpointing with interval: {} ms", getEndpoint().getCheckpointInterval());

            // Configure checkpointing mode
            if (getEndpoint().getCheckpointingMode() != null) {
                try {
                    CheckpointingMode mode =
                            CheckpointingMode.valueOf(getEndpoint().getCheckpointingMode());
                    env.getCheckpointConfig().setCheckpointingMode(mode);
                    LOG.info("Set checkpointing mode to: {}", mode);
                } catch (IllegalArgumentException e) {
                    LOG.warn(
                            "Invalid checkpointing mode '{}'. Valid values are: EXACTLY_ONCE, AT_LEAST_ONCE",
                            getEndpoint().getCheckpointingMode());
                }
            }

            // Configure checkpoint timeout
            if (getEndpoint().getCheckpointTimeout() != null) {
                env.getCheckpointConfig().setCheckpointTimeout(getEndpoint().getCheckpointTimeout());
                LOG.info("Set checkpoint timeout to: {} ms", getEndpoint().getCheckpointTimeout());
            }

            // Configure min pause between checkpoints
            if (getEndpoint().getMinPauseBetweenCheckpoints() != null) {
                env.getCheckpointConfig()
                        .setMinPauseBetweenCheckpoints(getEndpoint().getMinPauseBetweenCheckpoints());
                LOG.info(
                        "Set min pause between checkpoints to: {} ms",
                        getEndpoint().getMinPauseBetweenCheckpoints());
            }
        }

        LOG.debug("StreamExecutionEnvironment configuration completed");
    }
}
