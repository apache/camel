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
package org.apache.camel.component.spiffe;

import io.spiffe.workloadapi.DefaultWorkloadApiClient;
import io.spiffe.workloadapi.WorkloadApiClient;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SpiffeHealthCheckRegistrationTest {
    @Test
    void producersSharingAnEndpointOwnIndependentObservers() throws Exception {
        try (CamelContext context = new DefaultCamelContext();
             MockedStatic<DefaultWorkloadApiClient> factory = mockStatic(DefaultWorkloadApiClient.class)) {
            WorkloadApiClient endpointClient = mock(WorkloadApiClient.class);
            WorkloadApiClient firstObserver = mock(WorkloadApiClient.class);
            WorkloadApiClient secondObserver = mock(WorkloadApiClient.class);
            factory.when(DefaultWorkloadApiClient::newClient).thenReturn(endpointClient, firstObserver, secondObserver);
            WritableHealthCheckRepository repository = repository(context);
            repository.setEnabled(true);
            SpiffeEndpoint endpoint = endpoint(context, new SpiffeConfiguration());
            endpoint.start();
            SpiffeProducer first = new SpiffeProducer(endpoint);
            SpiffeProducer second = new SpiffeProducer(endpoint);
            try {
                first.start();
                second.start();
                assertThat(repository.stream().map(check -> check.getId()).toList()).hasSize(2).doesNotHaveDuplicates();
                first.stop();
                assertThat(repository.stream().toList()).hasSize(1);
                verify(firstObserver).close();
                verify(secondObserver, never()).close();
                second.stop();
                assertThat(repository.stream().toList()).isEmpty();
                verify(secondObserver).close();
            } finally {
                first.stop();
                second.stop();
                endpoint.stop();
            }
        }
    }

    @Test
    void observerStartupFailureIsDownAndClosesItsClient() throws Exception {
        try (CamelContext context = new DefaultCamelContext();
             MockedStatic<DefaultWorkloadApiClient> factory = mockStatic(DefaultWorkloadApiClient.class)) {
            WorkloadApiClient endpointClient = mock(WorkloadApiClient.class);
            WorkloadApiClient observer = mock(WorkloadApiClient.class);
            doThrow(new IllegalStateException("private failure details")).when(observer).watchX509Context(any());
            factory.when(DefaultWorkloadApiClient::newClient).thenReturn(endpointClient, observer);
            WritableHealthCheckRepository repository = repository(context);
            repository.setEnabled(true);
            SpiffeEndpoint endpoint = endpoint(context, new SpiffeConfiguration());
            endpoint.start();
            SpiffeProducer producer = new SpiffeProducer(endpoint);
            try {
                producer.start();
                assertThat(repository.stream().findFirst().orElseThrow().call().getDetails())
                        .containsEntry("spiffe.connection", "disconnected");
                verify(observer).close();
            } finally {
                producer.stop();
                endpoint.stop();
            }
            verify(observer).close();
        }
    }

    @Test
    void startsOneObserverPerProducerLifecycleAndRemovesItOnStop() throws Exception {
        try (CamelContext context = new DefaultCamelContext();
             MockedStatic<DefaultWorkloadApiClient> factory = mockStatic(DefaultWorkloadApiClient.class)) {
            WorkloadApiClient endpointClient = mock(WorkloadApiClient.class);
            WorkloadApiClient firstObserver = mock(WorkloadApiClient.class);
            WorkloadApiClient secondObserver = mock(WorkloadApiClient.class);
            factory.when(DefaultWorkloadApiClient::newClient).thenReturn(endpointClient, firstObserver, secondObserver);
            WritableHealthCheckRepository repository = repository(context);
            repository.setEnabled(true);
            SpiffeEndpoint endpoint = endpoint(context, new SpiffeConfiguration());
            endpoint.start();
            SpiffeProducer producer = new SpiffeProducer(endpoint);
            try {
                producer.start();
                assertThat(repository.stream().toList()).hasSize(1);
                verify(firstObserver).watchX509Context(any());
                producer.stop();
                assertThat(repository.stream().toList()).isEmpty();
                verify(firstObserver).close();
                verify(endpointClient, never()).close();

                producer.start();
                assertThat(repository.stream().toList()).hasSize(1);
                verify(secondObserver).watchX509Context(any());
                producer.stop();
                assertThat(repository.stream().toList()).isEmpty();
                verify(firstObserver).close();
                verify(secondObserver).close();
            } finally {
                producer.stop();
                endpoint.stop();
            }
            verify(endpointClient).close();
        }
    }

    @Test
    void disabledProducerHealthDoesNotOpenAnObserverClient() throws Exception {
        try (CamelContext context = new DefaultCamelContext();
             MockedStatic<DefaultWorkloadApiClient> factory = mockStatic(DefaultWorkloadApiClient.class)) {
            WorkloadApiClient endpointClient = mock(WorkloadApiClient.class);
            factory.when(DefaultWorkloadApiClient::newClient).thenReturn(endpointClient);
            WritableHealthCheckRepository repository = repository(context);
            SpiffeEndpoint endpoint = endpoint(context, new SpiffeConfiguration());
            endpoint.start();
            SpiffeProducer producer = new SpiffeProducer(endpoint);
            try {
                producer.start();
                factory.verify(DefaultWorkloadApiClient::newClient, times(1));
                repository.setEnabled(true);
                assertThat(repository.stream().toList()).hasSize(1);
                assertThat(repository.stream().findFirst().orElseThrow().call().getDetails())
                        .containsEntry("spiffe.connection", "not-started");
            } finally {
                producer.stop();
                endpoint.stop();
            }
        }
    }

    @Test
    void injectedClientsAreNeitherProbedNorClosed() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            WorkloadApiClient client = mock(WorkloadApiClient.class);
            SpiffeConfiguration configuration = new SpiffeConfiguration();
            configuration.setWorkloadApiClient(client);
            WritableHealthCheckRepository repository = repository(context);
            repository.setEnabled(true);
            SpiffeEndpoint endpoint = endpoint(context, configuration);
            endpoint.start();
            SpiffeProducer producer = new SpiffeProducer(endpoint);
            try {
                producer.start();
                assertThat(repository.stream().toList()).isEmpty();
                verify(client, never()).watchX509Context(any());
                verify(client, never()).watchJwtBundles(any());
            } finally {
                producer.stop();
                endpoint.stop();
            }
            verify(client, never()).close();
        }
    }

    private static WritableHealthCheckRepository repository(CamelContext context) {
        WritableHealthCheckRepository repository = HealthCheckHelper.getHealthCheckRepository(
                context, "producers", WritableHealthCheckRepository.class);
        assertThat(repository).isNotNull();
        return repository;
    }

    private static SpiffeEndpoint endpoint(CamelContext context, SpiffeConfiguration configuration) {
        return new SpiffeEndpoint("spiffe:health", new SpiffeComponent(context), configuration);
    }
}
