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
package org.apache.camel.component.knative;

import org.apache.camel.CamelContext;
import org.apache.camel.component.knative.spi.Knative;
import org.apache.camel.component.knative.spi.KnativeSinkBinding;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRepository;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.health.ProducersHealthCheckRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KnativeSinkBindingTest {

    @Test
    void testSinkBindingHealthCheckUP() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            HealthCheckRepository producersHc = HealthCheckHelper.getHealthCheckRepository(
                    context,
                    ProducersHealthCheckRepository.REPOSITORY_ID,
                    HealthCheckRepository.class);

            producersHc.setEnabled(true);

            KnativeSinkBinding sb = new KnativeSinkBinding();
            sb.setName("sb");
            sb.setType(Knative.Type.channel);
            sb.setObjectKind("InMemoryChannel");
            sb.setObjectApiVersion("messaging.knative.dev/v1");

            KnativeComponent component = new KnativeComponent();
            component.setHealthCheckProducerEnabled(true);
            component.getConfiguration().setSinkBinding(sb);
            component.setConsumerFactory(new KnativeTransportNoop());
            component.setProducerFactory(new KnativeTransportNoop());

            context.addComponent(KnativeConstants.SCHEME, component);
            context.getPropertiesComponent().addOverrideProperty("k.sink", "http://sb.scv.cluster.local:8081");

            KnativeEndpoint endpoint = context.getEndpoint("knative:channel/sb", KnativeEndpoint.class);
            assertThat(endpoint).isNotNull();
            assertThat(endpoint.lookupServiceDefinition("sb", Knative.EndpointKind.sink))
                    .isPresent().get()
                    .satisfies(res -> {
                        assertThat(res.getObjectKind()).isEqualTo(sb.getObjectKind());
                        assertThat(res.getObjectApiVersion()).isEqualTo(sb.getObjectApiVersion());
                        assertThat(res.getType()).isEqualTo(sb.getType());
                        assertThat(res.getUrl()).isEqualTo("http://sb.scv.cluster.local:8081");
                    });

            KnativeProducer producer = (KnativeProducer) endpoint.createProducer();
            assertThat(producer).isNotNull();

            producer.start();

            assertThat(producersHc).isNotNull();

            assertThat(producersHc.getCheck(endpoint.getId()).map(HealthCheck::call))
                    .isPresent().get()
                    .hasFieldOrPropertyWithValue("state", HealthCheck.State.UP);

        }
    }

    @Test
    void testSinkBindingHealthCheckDown() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            HealthCheckRepository producersHc = HealthCheckHelper.getHealthCheckRepository(
                    context,
                    ProducersHealthCheckRepository.REPOSITORY_ID,
                    HealthCheckRepository.class);

            producersHc.setEnabled(true);

            KnativeSinkBinding sb = new KnativeSinkBinding();
            sb.setName("sb");
            sb.setType(Knative.Type.channel);
            sb.setObjectKind("InMemoryChannel");
            sb.setObjectApiVersion("messaging.knative.dev/v1");

            KnativeComponent component = new KnativeComponent();
            component.setHealthCheckProducerEnabled(true);
            component.getConfiguration().setSinkBinding(sb);
            component.setConsumerFactory(new KnativeTransportNoop());
            component.setProducerFactory(new KnativeTransportNoop());

            context.addComponent(KnativeConstants.SCHEME, component);

            KnativeEndpoint endpoint = context.getEndpoint("knative:channel/sb", KnativeEndpoint.class);
            assertThat(endpoint).isNotNull();
            assertThat(endpoint.lookupServiceDefinition("sb", Knative.EndpointKind.sink))
                    .isPresent().get().satisfies(res -> {
                        assertThat(res.getObjectKind()).isEqualTo(sb.getObjectKind());
                        assertThat(res.getObjectApiVersion()).isEqualTo(sb.getObjectApiVersion());
                        assertThat(res.getType()).isEqualTo(sb.getType());
                        assertThat(res.getUrl()).isNull();
                    });

            KnativeProducer producer = (KnativeProducer) endpoint.createProducer();
            assertThat(producer).isNotNull();

            producer.start();

            assertThat(producersHc).isNotNull();

            assertThat(producersHc.getCheck(endpoint.getId()).map(HealthCheck::call))
                    .isPresent().get()
                    .hasFieldOrPropertyWithValue("state", HealthCheck.State.DOWN);

        }
    }
}
