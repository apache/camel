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
import org.apache.camel.Exchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataStreamComponentOptionsAndCollectTest extends CamelTestSupport {

    static StreamExecutionEnvironment streamExecutionEnvironment = Flinks.createStreamExecutionEnvironment();

    @BindToRegistry("myDataStream")
    private DataStreamSource<String> dss = streamExecutionEnvironment.readTextFile("src/test/resources/testds.txt");

    @Test
    void shouldUseDataStreamAndCallbackOfComponent() {
        AtomicReference<DataStream> used = new AtomicReference<>();
        FlinkComponent flink = context.getComponent("flink", FlinkComponent.class);
        flink.setDataStream(dss);
        flink.setDataStreamCallback(new VoidDataStreamCallback() {
            @Override
            public void doOnDataStream(DataStream ds, Object... payloads) {
                used.set(ds);
            }
        });

        Exchange result = template.send("flink:datastream", exchange -> exchange.getIn().setBody("hello"));

        assertThat(result.getException()).isNull();
        assertThat(used.get()).isSameAs(dss);
    }

    @Test
    void shouldReturnDataStreamWithDefaultCollect() {
        Exchange result = template.send("flink:datastream?dataStream=#myDataStream",
                exchange -> exchange.getIn().setHeader(FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                        (DataStreamCallback<DataStream>) (ds, payloads) -> ds.rebalance()));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isInstanceOf(DataStream.class).isNotSameAs(dss);
        assertThat(result.getMessage().getHeader(FlinkConstants.FLINK_DATASTREAM_HEADER))
                .isSameAs(result.getMessage().getBody());
    }

    @Test
    void shouldReturnDataStreamWithCollectFalse() {
        Exchange result = template.send("flink:datastream?dataStream=#myDataStream&collect=false",
                exchange -> exchange.getIn().setHeader(FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                        (DataStreamCallback<DataStream>) (ds, payloads) -> ds.rebalance()));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isInstanceOf(DataStream.class);
    }

    @Test
    void shouldFailToReturnDataStreamWithExplicitCollect() {
        Exchange result = template.send("flink:datastream?dataStream=#myDataStream&collect=true",
                exchange -> exchange.getIn().setHeader(FlinkConstants.FLINK_DATASTREAM_CALLBACK_HEADER,
                        (DataStreamCallback<DataStream>) (ds, payloads) -> ds.rebalance()));

        assertThat(result.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collect mode not supported for Flink DataStreams");
    }
}
