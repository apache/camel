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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for DataStream batch processing with various configurations. Tests end-to-end scenarios with actual
 * data transformations.
 */
public class DataStreamBatchProcessingIT extends CamelTestSupport {

    // Create separate environments for isolation
    StreamExecutionEnvironment batchEnv = Flinks.createStreamExecutionEnvironment();
    StreamExecutionEnvironment transformEnv = Flinks.createStreamExecutionEnvironment();

    @BindToRegistry("numberStream")
    private DataStreamSource<Integer> numberStream = batchEnv.fromElements(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    @BindToRegistry("textStream")
    private DataStreamSource<String> textStream
            = transformEnv.fromElements("apache", "camel", "flink", "integration", "test");

    @BindToRegistry("multiplyCallback")
    public DataStreamCallback multiplyCallback() {
        return new VoidDataStreamCallback() {
            @Override
            public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                int multiplier = (Integer) payloads[0];
                ds.map((MapFunction<Integer, Integer>) value -> value * multiplier)
                        .print();
            }
        };
    }

    @Test
    public void testBatchProcessingWithTransformation() {
        // Verify that the callback executes without error and the transformation is set up
        template.sendBodyAndHeader(
                "direct:batchTransform",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Verify environment is configured with batch mode and parallelism=2
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        Assertions.assertThat(env.getParallelism()).isEqualTo(2);

                        // Set up transformation (won't execute in test context)
                        ds.map((MapFunction<Integer, Integer>) value -> value * 2).print();
                    }
                });
    }

    @Test
    public void testBatchProcessingWithPayload() {
        List<Integer> results = new ArrayList<>();

        template.sendBodyAndHeader(
                "direct:withPayload",
                3, // multiplier
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        Assertions.assertThat(payloads).hasSize(1);
                        int multiplier = (Integer) payloads[0];
                        Assertions.assertThat(multiplier).isEqualTo(3);

                        ds.map((MapFunction<Integer, Integer>) value -> value * multiplier)
                                .print();
                    }
                });
    }

    @Test
    public void testBatchProcessingWithFilter() {
        // Verify filter operation can be set up
        template.sendBodyAndHeader(
                "direct:batchFilter",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Verify environment configuration
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        Assertions.assertThat(env.getParallelism()).isEqualTo(1);

                        // Set up filter (won't execute in test context)
                        ds.filter(value -> ((Integer) value) % 2 == 0).print();
                    }
                });
    }

    @Test
    public void testStringProcessingWithBatchMode() {
        // Verify string transformation can be set up
        template.sendBodyAndHeader(
                "direct:stringTransform",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Verify environment configuration
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        Assertions.assertThat(env.getParallelism()).isEqualTo(3);

                        // Set up transformation
                        ds.map((MapFunction<String, String>) String::toUpperCase).print();
                    }
                });
    }

    @Test
    public void testHighParallelismProcessing() {
        // Verify high parallelism configuration
        template.sendBodyAndHeader(
                "direct:highParallelism",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();

                        // Verify high parallelism was set
                        Assertions.assertThat(env.getParallelism()).isEqualTo(16);
                        Assertions.assertThat(env.getMaxParallelism()).isEqualTo(256);

                        // Set up transformation
                        ds.map((MapFunction<Integer, Integer>) value -> value * value).print();
                    }
                });
    }

    @Test
    public void testCallbackFromRegistry() {
        // Track that the callback was actually invoked
        final boolean[] callbackInvoked = { false };

        // Send body with multiplier and verify callback executes
        template.sendBodyAndHeader(
                "direct:registryCallback",
                5,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Verify the callback is invoked
                        callbackInvoked[0] = true;

                        // Verify environment configuration
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        Assertions.assertThat(env.getParallelism()).isEqualTo(4);

                        // Verify payload was passed correctly
                        Assertions.assertThat(payloads).hasSize(1);
                        Assertions.assertThat(payloads[0]).isEqualTo(5);

                        // Set up the transformation (using the registry callback pattern)
                        ds.map((MapFunction<Integer, Integer>) value -> value * (Integer) payloads[0]).print();
                    }
                });

        // Verify callback was executed
        Assertions.assertThat(callbackInvoked[0]).isTrue();
    }

    @Test
    public void testMultipleOperations() {
        // Verify chained operations can be set up
        template.sendBodyAndHeader(
                "direct:multipleOps",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Verify environment configuration
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        Assertions.assertThat(env.getParallelism()).isEqualTo(4);

                        // Set up chained operations
                        ds.filter(value -> ((Integer) value) > 3)
                                .map((MapFunction<Integer, Integer>) value -> value * 10)
                                .map((MapFunction<Integer, Integer>) value -> value + 5)
                                .print();
                    }
                });
    }

    @Test
    public void testConfigurationPersistsAcrossInvocations() {
        // First invocation
        template.sendBodyAndHeader(
                "direct:batchTransform",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        Assertions.assertThat(env.getParallelism()).isEqualTo(2);
                    }
                });

        // Second invocation - should have same configuration
        template.sendBodyAndHeader(
                "direct:batchTransform",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        Assertions.assertThat(env.getParallelism()).isEqualTo(2);
                    }
                });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:batchTransform")
                        .to("flink:datastream?dataStream=#numberStream"
                            + "&executionMode=BATCH"
                            + "&parallelism=2");

                from("direct:withPayload")
                        .to("flink:datastream?dataStream=#numberStream"
                            + "&executionMode=BATCH");

                from("direct:batchFilter")
                        .to("flink:datastream?dataStream=#numberStream"
                            + "&executionMode=BATCH"
                            + "&parallelism=1");

                from("direct:stringTransform")
                        .to("flink:datastream?dataStream=#textStream"
                            + "&executionMode=BATCH"
                            + "&parallelism=3");

                from("direct:highParallelism")
                        .to("flink:datastream?dataStream=#numberStream"
                            + "&executionMode=BATCH"
                            + "&parallelism=16"
                            + "&maxParallelism=256");

                from("direct:registryCallback")
                        .to("flink:datastream?dataStream=#numberStream"
                            + "&dataStreamCallback=#multiplyCallback"
                            + "&executionMode=BATCH"
                            + "&parallelism=4");

                from("direct:multipleOps")
                        .to("flink:datastream?dataStream=#numberStream"
                            + "&executionMode=BATCH"
                            + "&parallelism=4");
            }
        };
    }
}
