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

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.knative.spi.KnativeConsumerFactory;
import org.apache.camel.component.knative.spi.KnativeProducerFactory;
import org.apache.camel.component.knative.spi.KnativeResource;
import org.apache.camel.component.knative.spi.KnativeTransportConfiguration;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultProducer;

public class KnativeTransportNoop implements KnativeConsumerFactory, KnativeProducerFactory {
    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public Producer createProducer(Endpoint endpoint, KnativeTransportConfiguration configuration, KnativeResource service) {
        return new DefaultProducer(endpoint) {
            @Override
            public void process(Exchange exchange) {
            }
        };
    }

    @Override
    public Consumer createConsumer(
            Endpoint endpoint, KnativeTransportConfiguration configuration, KnativeResource service, Processor processor) {
        return new DefaultConsumer(endpoint, processor);
    }
}
