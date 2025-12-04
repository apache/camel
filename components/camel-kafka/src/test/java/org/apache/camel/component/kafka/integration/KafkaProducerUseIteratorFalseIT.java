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

package org.apache.camel.component.kafka.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.MockConsumerInterceptor;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KafkaProducerUseIteratorFalseIT extends BaseKafkaTestSupport {

    private static final String TOPIC = "use-iterator-false";

    private static final String FROM_URI = "kafka:" + TOPIC
            + "?groupId=KafkaProducerUseIteratorFalseIT&autoOffsetReset=earliest&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer&"
            + "valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
            + "&autoCommitIntervalMs=1000&pollTimeoutMs=1000&autoCommitEnable=true&interceptorClasses=org.apache.camel.component.kafka.MockConsumerInterceptor";

    @BeforeEach
    public void init() {
        MockConsumerInterceptor.recordsCaptured.clear();
    }

    @AfterEach
    public void after() {
        // clean all test topics
        kafkaAdminClient.deleteTopics(Collections.singletonList(TOPIC));
    }

    @Test
    public void testUseIteratorFalse() throws Exception {
        List<String> body = new ArrayList<>();
        body.add("first");
        body.add("second");

        MockEndpoint mock = contextExtension.getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(body.toString());

        contextExtension.getProducerTemplate().sendBody("direct:start", body);

        mock.assertIsSatisfied(5000);

        assertEquals(
                1,
                MockConsumerInterceptor.recordsCaptured.stream()
                        .flatMap(i -> StreamSupport.stream(i.records(TOPIC).spliterator(), false))
                        .count());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("kafka:" + TOPIC + "?groupId=KafkaProducerUseIteratorFalseIT&useIterator=false");

                from(FROM_URI).to("mock:result");
            }
        };
    }
}
