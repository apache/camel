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
package org.apache.camel.component.dynamicrouter;

import org.apache.camel.Producer;
import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class DynamicRouterEndpointTest extends DynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        endpoint = new DynamicRouterEndpoint(
                BASE_URI, component, configuration, () -> processorFactory, () -> producerFactory,
                () -> filterProcessorFactory);
    }

    @Test
    void testCreateProducer() {
        Producer actualProducer = endpoint.createProducer();
        assertEquals(producer, actualProducer);
    }

    @Test
    void testCreateConsumerException() {
        assertThrows(IllegalStateException.class, () -> endpoint.createConsumer(processor));
    }

    @Test
    void testCreateControlChannelEndpointWithError() {
        doThrow(new RuntimeException()).when(component)
                .setControlChannelProcessor(any(DynamicRouterControlChannelProcessor.class));
        assertThrows(IllegalStateException.class, () -> new DynamicRouterEndpoint(
                BASE_URI, component, configuration, () -> controlChannelProcessorFactory, () -> controlProducerFactory));
    }
}
