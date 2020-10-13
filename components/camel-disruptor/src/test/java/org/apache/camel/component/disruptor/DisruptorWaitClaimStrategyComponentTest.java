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
package org.apache.camel.component.disruptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the WaitStrategy and ClaimStrategy configuration of the disruptor component
 */
public class DisruptorWaitClaimStrategyComponentTest extends CamelTestSupport {
    private static final Integer VALUE = Integer.valueOf(42);
    private static final List<String> DISRUPTOR_URIS = new ArrayList<>();
    private static final List<String> MOCK_URIS = new ArrayList<>();

    @Produce
    protected ProducerTemplate template;

    @BeforeAll
    public static void initDisruptorUris() {
        for (final DisruptorWaitStrategy waitStrategy : DisruptorWaitStrategy.values()) {
            for (final DisruptorProducerType producerType : DisruptorProducerType.values()) {
                String disruptorUri = "disruptor:test?waitStrategy=" + waitStrategy + "&producerType=" + producerType;
                DISRUPTOR_URIS.add(disruptorUri);
                MOCK_URIS.add("mock:result-" + waitStrategy + "-" + producerType);
            }
        }
    }

    public static List<Arguments> getTestArguments() {
        List<Arguments> arguments = new ArrayList<Arguments>();
        for (int i = 0; i < DISRUPTOR_URIS.size(); i++) {
            arguments.add(Arguments.of(DISRUPTOR_URIS.get(i), MOCK_URIS.get(i)));
        }
        return arguments;
    }

    @ParameterizedTest
    @MethodSource("getTestArguments")
    void testProduce(String disruptorUri, String mockUri) throws InterruptedException {

        MockEndpoint resultEndpoint = context.getEndpoint(mockUri, MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived(VALUE);
        resultEndpoint.setExpectedMessageCount(1);

        template.asyncSendBody(disruptorUri, VALUE);

        resultEndpoint.await(5, TimeUnit.SECONDS);
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (int i = 0; i < DISRUPTOR_URIS.size(); i++) {
                    from(DISRUPTOR_URIS.get(i)).to(MOCK_URIS.get(i));
                }
            }
        };
    }
}
