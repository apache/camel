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

import java.util.Arrays;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DataStreamProducerTest extends CamelTestSupport {

    static StreamExecutionEnvironment streamExecutionEnvironment = Flinks.createStreamExecutionEnvironment();

    String flinkDataStreamUri = "flink:dataStream?dataStream=#myDataStream";

    @BindToRegistry("myDataStream")
    private DataStreamSource<String> dss = streamExecutionEnvironment.readTextFile("src/test/resources/testds.txt");

    @Test
    public void shouldExecuteDataStreamCallback() {
        template.sendBodyAndHeader(flinkDataStreamUri, null, FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Just verify the callback is executed
                        ds.print();
                    }
                });
    }

    @Test
    public void shouldExecuteDataStreamCallbackWithPayload() {
        template.sendBodyAndHeader(flinkDataStreamUri, "test-payload",
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        Assertions.assertThat(payloads).hasSize(1);
                        Assertions.assertThat(payloads[0]).isEqualTo("test-payload");
                    }
                });
    }

    @Test
    public void shouldExecuteDataStreamCallbackWithMultiplePayloads() {
        List<String> payloads = Arrays.asList("payload1", "payload2", "payload3");
        template.sendBodyAndHeader(flinkDataStreamUri, payloads, FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        Assertions.assertThat(payloads).hasSize(3);
                        Assertions.assertThat(payloads[0]).isEqualTo("payload1");
                        Assertions.assertThat(payloads[1]).isEqualTo("payload2");
                        Assertions.assertThat(payloads[2]).isEqualTo("payload3");
                    }
                });
    }

    @Test
    public void shouldConfigureExecutionMode() {
        StreamExecutionEnvironment env = streamExecutionEnvironment;
        env.setRuntimeMode(RuntimeExecutionMode.BATCH);

        Assertions.assertThat(env.getConfiguration().get(
                org.apache.flink.configuration.ExecutionOptions.RUNTIME_MODE))
                .isEqualTo(RuntimeExecutionMode.BATCH);
    }

    @Test
    public void shouldConfigureCheckpointing() {
        StreamExecutionEnvironment env = Flinks.createStreamExecutionEnvironment();
        env.enableCheckpointing(5000);
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

        Assertions.assertThat(env.getCheckpointConfig().getCheckpointInterval()).isEqualTo(5000);
        Assertions.assertThat(env.getCheckpointConfig().getCheckpointingMode())
                .isEqualTo(CheckpointingMode.EXACTLY_ONCE);
    }

    @Test
    public void shouldConfigureParallelism() {
        StreamExecutionEnvironment env = Flinks.createStreamExecutionEnvironment();
        env.setParallelism(4);

        Assertions.assertThat(env.getParallelism()).isEqualTo(4);
    }

    @Test
    public void shouldConfigureMaxParallelism() {
        StreamExecutionEnvironment env = Flinks.createStreamExecutionEnvironment();
        env.setMaxParallelism(128);

        Assertions.assertThat(env.getMaxParallelism()).isEqualTo(128);
    }
}
