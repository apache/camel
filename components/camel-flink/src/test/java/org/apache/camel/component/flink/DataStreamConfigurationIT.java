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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for DataStream producer configuration options including execution mode, checkpointing, and
 * parallelism settings.
 */
public class DataStreamConfigurationIT extends CamelTestSupport {

    @BindToRegistry("batchDataStream")
    public DataStreamSource<String> createBatchDataStream() {
        return Flinks.createStreamExecutionEnvironment().fromElements("test1", "test2", "test3");
    }

    @BindToRegistry("streamingDataStream")
    public DataStreamSource<String> createStreamingDataStream() {
        return Flinks.createStreamExecutionEnvironment().fromElements("stream1", "stream2");
    }

    @BindToRegistry("checkpointDataStream")
    public DataStreamSource<String> createCheckpointDataStream() {
        return Flinks.createStreamExecutionEnvironment().fromElements("data1", "data2");
    }

    @BindToRegistry("parallelismDataStream")
    public DataStreamSource<String> createParallelismDataStream() {
        return Flinks.createStreamExecutionEnvironment().fromElements("parallel1", "parallel2");
    }

    @BindToRegistry("fullConfigDataStream")
    public DataStreamSource<String> createFullConfigDataStream() {
        return Flinks.createStreamExecutionEnvironment().fromElements("config1", "config2");
    }

    @BindToRegistry("captureEnvCallback")
    public DataStreamCallback captureEnvCallback() {
        return new VoidDataStreamCallback() {
            @Override
            public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                // Just capture the environment for testing
                ds.print();
            }
        };
    }

    @Test
    public void testBatchExecutionModeConfiguration() {
        AtomicReference<StreamExecutionEnvironment> envRef = new AtomicReference<>();

        template.sendBodyAndHeader(
                "direct:batchMode",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        envRef.set(env);
                    }
                });

        StreamExecutionEnvironment env = envRef.get();
        Assertions.assertThat(env).isNotNull();

        // Verify BATCH mode was set
        RuntimeExecutionMode mode = env.getConfiguration()
                .get(org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE);
        Assertions.assertThat(mode).isEqualTo(RuntimeExecutionMode.BATCH);
    }

    @Test
    public void testStreamingExecutionModeConfiguration() {
        AtomicReference<StreamExecutionEnvironment> envRef = new AtomicReference<>();

        template.sendBodyAndHeader(
                "direct:streamingMode",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        envRef.set(env);
                    }
                });

        StreamExecutionEnvironment env = envRef.get();
        Assertions.assertThat(env).isNotNull();

        // Verify STREAMING mode was set
        RuntimeExecutionMode mode = env.getConfiguration()
                .get(org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE);
        Assertions.assertThat(mode).isEqualTo(RuntimeExecutionMode.STREAMING);
    }

    @Test
    public void testCheckpointingConfiguration() {
        AtomicReference<CheckpointConfig> checkpointConfigRef = new AtomicReference<>();

        template.sendBodyAndHeader(
                "direct:checkpointing",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        checkpointConfigRef.set(env.getCheckpointConfig());
                    }
                });

        CheckpointConfig config = checkpointConfigRef.get();
        Assertions.assertThat(config).isNotNull();

        // Verify checkpoint interval
        Assertions.assertThat(config.getCheckpointInterval()).isEqualTo(5000L);

        // Verify checkpointing mode
        Assertions.assertThat(config.getCheckpointingMode()).isEqualTo(CheckpointingMode.EXACTLY_ONCE);

        // Verify checkpoint timeout
        Assertions.assertThat(config.getCheckpointTimeout()).isEqualTo(30000L);

        // Verify min pause between checkpoints
        Assertions.assertThat(config.getMinPauseBetweenCheckpoints()).isEqualTo(2000L);
    }

    @Test
    public void testParallelismConfiguration() {
        AtomicReference<StreamExecutionEnvironment> envRef = new AtomicReference<>();

        template.sendBodyAndHeader(
                "direct:parallelism",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        envRef.set(env);
                    }
                });

        StreamExecutionEnvironment env = envRef.get();
        Assertions.assertThat(env).isNotNull();

        // Verify parallelism settings
        Assertions.assertThat(env.getParallelism()).isEqualTo(4);
        Assertions.assertThat(env.getMaxParallelism()).isEqualTo(64);
    }

    @Test
    public void testFullConfiguration() {
        AtomicReference<StreamExecutionEnvironment> envRef = new AtomicReference<>();
        AtomicReference<CheckpointConfig> checkpointConfigRef = new AtomicReference<>();

        template.sendBodyAndHeader(
                "direct:fullConfig",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        envRef.set(env);
                        checkpointConfigRef.set(env.getCheckpointConfig());
                    }
                });

        StreamExecutionEnvironment env = envRef.get();
        CheckpointConfig checkpointConfig = checkpointConfigRef.get();

        Assertions.assertThat(env).isNotNull();
        Assertions.assertThat(checkpointConfig).isNotNull();

        // Verify execution mode
        RuntimeExecutionMode mode = env.getConfiguration()
                .get(org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE);
        Assertions.assertThat(mode).isEqualTo(RuntimeExecutionMode.STREAMING);

        // Verify parallelism
        Assertions.assertThat(env.getParallelism()).isEqualTo(8);
        Assertions.assertThat(env.getMaxParallelism()).isEqualTo(128);

        // Verify checkpointing
        Assertions.assertThat(checkpointConfig.getCheckpointInterval()).isEqualTo(10000L);
        Assertions.assertThat(checkpointConfig.getCheckpointingMode()).isEqualTo(CheckpointingMode.AT_LEAST_ONCE);
        Assertions.assertThat(checkpointConfig.getCheckpointTimeout()).isEqualTo(60000L);
        Assertions.assertThat(checkpointConfig.getMinPauseBetweenCheckpoints()).isEqualTo(5000L);
    }

    @Test
    public void testInvalidExecutionModeHandling() {
        // Should not throw exception, just log warning
        template.sendBodyAndHeader(
                "direct:invalidMode",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Should execute without error despite invalid mode
                        Assertions.assertThat(ds).isNotNull();
                    }
                });
    }

    @Test
    public void testInvalidCheckpointingModeHandling() {
        // Should not throw exception, just log warning
        template.sendBodyAndHeader(
                "direct:invalidCheckpointMode",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Should execute without error despite invalid checkpoint mode
                        Assertions.assertThat(ds).isNotNull();
                    }
                });
    }

    @Test
    public void testConfigurationViaRouteParameters() {
        AtomicReference<StreamExecutionEnvironment> envRef = new AtomicReference<>();

        template.sendBodyAndHeader(
                "direct:routeConfig",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        envRef.set(env);
                    }
                });

        StreamExecutionEnvironment env = envRef.get();
        Assertions.assertThat(env).isNotNull();

        // Verify the configuration was applied via route parameters
        Assertions.assertThat(env.getParallelism()).isEqualTo(2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:batchMode")
                        .to("flink:datastream?dataStream=#batchDataStream"
                            + "&executionMode=BATCH");

                from("direct:streamingMode")
                        .to("flink:datastream?dataStream=#streamingDataStream"
                            + "&executionMode=STREAMING");

                from("direct:checkpointing")
                        .to("flink:datastream?dataStream=#checkpointDataStream"
                            + "&checkpointInterval=5000"
                            + "&checkpointingMode=EXACTLY_ONCE"
                            + "&checkpointTimeout=30000"
                            + "&minPauseBetweenCheckpoints=2000");

                from("direct:parallelism")
                        .to("flink:datastream?dataStream=#parallelismDataStream"
                            + "&parallelism=4"
                            + "&maxParallelism=64");

                from("direct:fullConfig")
                        .to("flink:datastream?dataStream=#fullConfigDataStream"
                            + "&executionMode=STREAMING"
                            + "&checkpointInterval=10000"
                            + "&checkpointingMode=AT_LEAST_ONCE"
                            + "&checkpointTimeout=60000"
                            + "&minPauseBetweenCheckpoints=5000"
                            + "&parallelism=8"
                            + "&maxParallelism=128"
                            + "&jobName=FullConfigTest");

                from("direct:invalidMode")
                        .to("flink:datastream?dataStream=#batchDataStream"
                            + "&executionMode=INVALID_MODE"
                            + "&dataStreamCallback=#captureEnvCallback");

                from("direct:invalidCheckpointMode")
                        .to("flink:datastream?dataStream=#checkpointDataStream"
                            + "&checkpointInterval=5000"
                            + "&checkpointingMode=INVALID_CHECKPOINT_MODE"
                            + "&dataStreamCallback=#captureEnvCallback");

                from("direct:routeConfig")
                        .to("flink:datastream?dataStream=#parallelismDataStream"
                            + "&parallelism=2");
            }
        };
    }
}
