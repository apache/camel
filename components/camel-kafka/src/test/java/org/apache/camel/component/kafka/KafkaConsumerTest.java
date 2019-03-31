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
package org.apache.camel.component.kafka;

import org.apache.camel.Processor;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KafkaConsumerTest {

    private KafkaConfiguration configuration = mock(KafkaConfiguration.class);
    private KafkaComponent component = mock(KafkaComponent.class);
    private KafkaEndpoint endpoint = mock(KafkaEndpoint.class);
    private Processor processor = mock(Processor.class);

    @Test(expected = IllegalArgumentException.class)
    public void consumerRequiresBootstrapServers() throws Exception {
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getConfiguration().getGroupId()).thenReturn("groupOne");
        new KafkaConsumer(endpoint, processor);
    }

    @Test
    public void consumerOnlyRequiresBootstrapServers() throws Exception {
        when(endpoint.getComponent()).thenReturn(component);
        when(endpoint.getConfiguration()).thenReturn(configuration);
        when(endpoint.getConfiguration().getBrokers()).thenReturn("localhost:2181");
        new KafkaConsumer(endpoint, processor);
    }
}
