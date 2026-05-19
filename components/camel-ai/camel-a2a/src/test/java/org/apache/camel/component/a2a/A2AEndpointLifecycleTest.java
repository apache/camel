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
package org.apache.camel.component.a2a;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2AEndpointLifecycleTest {

    @Test
    void producerOnlyEndpointDoesNotCreateConsumerTaskStore() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            A2AEndpoint endpoint = endpoint(context, "producer-agent");
            endpoint.createProducer();

            endpoint.doStart();
            try {
                assertThat(endpoint.getTaskStore()).isNull();
                assertThat(endpoint.getHttpClient()).isNotNull();
                assertThat(endpoint.getResolvedCard().getName()).isEqualTo("producer-agent");
            } finally {
                endpoint.doStop();
            }
        }
    }

    @Test
    void consumerEndpointCreatesTaskStore() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            A2AEndpoint endpoint = endpoint(context, "consumer-agent");
            endpoint.createConsumer(exchange -> {
            });

            endpoint.doStart();
            try {
                assertThat(endpoint.getTaskStore()).isNotNull();
                assertThat(endpoint.getHttpClient()).isNotNull();
                assertThat(endpoint.getResolvedCard().getName()).isEqualTo("consumer-agent");
            } finally {
                endpoint.doStop();
            }
        }
    }

    private static A2AEndpoint endpoint(DefaultCamelContext context, String agentCardSource) {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:" + agentCardSource, component, new A2AConfiguration());
        endpoint.setAgentCardSource(agentCardSource);
        endpoint.setCamelContext(context);
        return endpoint;
    }
}
