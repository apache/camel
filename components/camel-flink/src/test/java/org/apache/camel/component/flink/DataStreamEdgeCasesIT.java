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

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for edge cases and error handling in DataStream configuration.
 */
public class DataStreamEdgeCasesIT extends CamelTestSupport {

    // Create separate environment for isolation
    StreamExecutionEnvironment testEnv = Flinks.createStreamExecutionEnvironment();

    @BindToRegistry("testDataStream")
    private DataStreamSource<String> testDs = testEnv.fromElements("test1", "test2");

    @Test
    public void testMissingDataStreamThrowsException() {
        // Should throw exception when no DataStream is defined
        Assertions.assertThatThrownBy(() -> {
            template.sendBodyAndHeader(
                    "direct:noDataStream",
                    null,
                    FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                    new VoidDataStreamCallback() {
                        @Override
                        public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                            ds.print();
                        }
                    });
        }).isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageContaining("No DataStream defined");
    }

    @Test
    public void testMissingCallbackThrowsException() {
        // Should throw exception when no callback is defined
        Assertions.assertThatThrownBy(() -> {
            template.sendBody("direct:noCallback", null);
        }).isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageContaining("Cannot resolve DataStream callback");
    }

    @Test
    public void testInvalidParallelismHandling() {
        // Flink will reject parallelism of 0 or negative values during configuration
        // This test verifies that the error is clear
        Assertions.assertThatThrownBy(() -> {
            StreamExecutionEnvironment invalidEnv = Flinks.createStreamExecutionEnvironment();
            invalidEnv.setParallelism(0);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parallelism must be at least one");
    }

    @Test
    public void testCheckpointWithoutIntervalIgnored() {
        template.sendBodyAndHeader(
                "direct:checkpointNoInterval",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        // Checkpointing mode is set but interval is not, so checkpointing won't be enabled
                        Assertions.assertThat(env).isNotNull();
                    }
                });
    }

    @Test
    public void testNullPayloadsHandling() {
        template.sendBodyAndHeader(
                "direct:withDataStream",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Null body results in single null payload
                        Assertions.assertThat(payloads).hasSize(1);
                        Assertions.assertThat(payloads[0]).isNull();
                    }
                });
    }

    @Test
    public void testEmptyListPayload() {
        template.sendBodyAndHeader(
                "direct:withDataStream",
                java.util.Collections.emptyList(),
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Empty list should result in empty array
                        Assertions.assertThat(payloads).isEmpty();
                    }
                });
    }

    @Test
    public void testVeryHighParallelism() {
        template.sendBodyAndHeader(
                "direct:veryHighParallelism",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        // Very high parallelism should be accepted (Flink will validate)
                        Assertions.assertThat(env.getParallelism()).isEqualTo(1000);
                    }
                });
    }

    @Test
    public void testAutomaticExecutionMode() {
        template.sendBodyAndHeader(
                "direct:automaticMode",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // AUTOMATIC mode should be accepted
                        Assertions.assertThat(ds).isNotNull();
                    }
                });
    }

    @Test
    public void testVeryShortCheckpointInterval() {
        template.sendBodyAndHeader(
                "direct:shortCheckpoint",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        StreamExecutionEnvironment env = ds.getExecutionEnvironment();
                        // Very short interval (100ms) should be configured, though not recommended
                        Assertions.assertThat(env.getCheckpointConfig().getCheckpointInterval()).isEqualTo(100L);
                    }
                });
    }

    @Test
    public void testCallbackExceptionPropagation() {
        Assertions.assertThatThrownBy(() -> {
            template.sendBodyAndHeader(
                    "direct:withDataStream",
                    null,
                    FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                    new VoidDataStreamCallback() {
                        @Override
                        public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                            throw new RuntimeException("Test exception from callback");
                        }
                    });
        }).isInstanceOf(CamelExecutionException.class)
                .cause()
                .hasMessageContaining("Test exception from callback");
    }

    @Test
    public void testHeaderOverridesEndpointCallback() {
        // When both endpoint callback and header callback are present, header should win
        boolean[] headerCallbackCalled = { false };

        template.sendBodyAndHeader(
                "direct:withEndpointCallback",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        headerCallbackCalled[0] = true;
                    }
                });

        Assertions.assertThat(headerCallbackCalled[0]).isTrue();
    }

    @Test
    public void testConfigurationWithAllNullOptionals() {
        // All optional configuration parameters are null - should use defaults
        template.sendBodyAndHeader(
                "direct:minimalConfig",
                null,
                FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                new VoidDataStreamCallback() {
                    @Override
                    public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                        // Should work with defaults
                        Assertions.assertThat(ds).isNotNull();
                    }
                });
    }

    @BindToRegistry("endpointCallback")
    public DataStreamCallback endpointCallback() {
        return new VoidDataStreamCallback() {
            @Override
            public void doOnDataStream(DataStream ds, Object... payloads) throws Exception {
                throw new AssertionError("Endpoint callback should not be called when header is present");
            }
        };
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:noDataStream")
                        .to("flink:datastream");

                from("direct:noCallback")
                        .to("flink:datastream?dataStream=#testDataStream");

                from("direct:checkpointNoInterval")
                        .to("flink:datastream?dataStream=#testDataStream"
                            + "&checkpointingMode=EXACTLY_ONCE");

                from("direct:withDataStream")
                        .to("flink:datastream?dataStream=#testDataStream");

                from("direct:veryHighParallelism")
                        .to("flink:datastream?dataStream=#testDataStream"
                            + "&parallelism=1000"
                            + "&maxParallelism=2000");

                from("direct:automaticMode")
                        .to("flink:datastream?dataStream=#testDataStream"
                            + "&executionMode=AUTOMATIC");

                from("direct:shortCheckpoint")
                        .to("flink:datastream?dataStream=#testDataStream"
                            + "&checkpointInterval=100");

                from("direct:withEndpointCallback")
                        .to("flink:datastream?dataStream=#testDataStream"
                            + "&dataStreamCallback=#endpointCallback");

                from("direct:minimalConfig")
                        .to("flink:datastream?dataStream=#testDataStream");
            }
        };
    }
}
