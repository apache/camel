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
package org.apache.camel.component.kafka.share;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KafkaShareComponentTest extends CamelTestSupport {

    @Test
    void endpointOptions() {
        KafkaShareEndpoint endpoint = context.getEndpoint(
                "kafka-share:orders,invoices?brokers=broker1:9092&groupId=workers&consumersCount=20&onFailure=REJECT"
                                                          + "&commitMode=ASYNC&additionalProperties.client.rack=rack-1",
                KafkaShareEndpoint.class);

        KafkaShareConfiguration configuration = endpoint.getConfiguration();
        assertThat(configuration.getTopic()).isEqualTo("orders,invoices");
        assertThat(configuration.getBrokers()).isEqualTo("broker1:9092");
        assertThat(configuration.getGroupId()).isEqualTo("workers");
        assertThat(configuration.getConsumersCount()).isEqualTo(20);
        assertThat(configuration.getOnFailure()).isEqualTo(KafkaShareAcknowledgeType.REJECT);
        assertThat(configuration.getCommitMode()).isEqualTo(KafkaShareCommitMode.ASYNC);
        assertThat(configuration.getAdditionalProperties()).containsEntry("client.rack", "rack-1");
    }

    @Test
    void endpointsDoNotShareTheComponentConfiguration() {
        KafkaShareEndpoint first = context.getEndpoint("kafka-share:orders?groupId=first&additionalProperties.a=1",
                KafkaShareEndpoint.class);
        KafkaShareEndpoint second = context.getEndpoint("kafka-share:orders?groupId=second", KafkaShareEndpoint.class);

        assertThat(first.getConfiguration()).isNotSameAs(second.getConfiguration());
        assertThat(second.getConfiguration().getAdditionalProperties()).doesNotContainKey("a");
    }

    @Test
    void producerIsNotSupported() {
        KafkaShareEndpoint endpoint = context.getEndpoint("kafka-share:orders?groupId=workers", KafkaShareEndpoint.class);

        assertThatThrownBy(endpoint::createProducer).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void consumerRequiresAShareGroup() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:orders?brokers=localhost:9092").routeId("noGroup").autoStartup(false).to("mock:result");
            }
        });

        assertThatThrownBy(() -> context.getRouteController().startRoute("noGroup"))
                .hasStackTraceContaining("The share group must be configured with the groupId option");
    }

    @Test
    void consumerFailsToStartWithAConsumerGroupOption() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:orders?brokers=localhost:9092&groupId=workers&additionalProperties.auto.offset.reset=earliest")
                        .routeId("consumerGroupOption").autoStartup(false).to("mock:result");
            }
        });

        assertThatThrownBy(() -> context.getRouteController().startRoute("consumerGroupOption"))
                .hasStackTraceContaining("The consumer group options [auto.offset.reset] cannot be set on a share consumer");
    }
}
