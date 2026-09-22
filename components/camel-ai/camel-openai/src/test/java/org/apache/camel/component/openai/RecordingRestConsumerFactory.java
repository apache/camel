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
package org.apache.camel.component.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Stands in for the HTTP server in the tests: it records what the webhook consumer registers and calls the processor
 * with a request built by the test, the way platform-http would.
 */
final class RecordingRestConsumerFactory implements RestConsumerFactory {

    private final List<Registration> registrations = new ArrayList<>();

    static RecordingRestConsumerFactory bindTo(CamelContext context) {
        RecordingRestConsumerFactory factory = new RecordingRestConsumerFactory();
        context.getRegistry().bind("recordingRestConsumerFactory", factory);
        return factory;
    }

    @Override
    public Consumer createConsumer(
            CamelContext camelContext, Processor processor, String verb, String basePath,
            String uriTemplate, String consumes, String produces, RestConfiguration configuration,
            Map<String, Object> parameters) {
        DefaultEndpoint endpoint = new DefaultEndpoint("recording-rest:" + registrations.size(), null) {
            @Override
            public Producer createProducer() {
                throw new UnsupportedOperationException("The recording REST endpoint only consumes");
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                throw new UnsupportedOperationException("The factory creates the recording REST consumers");
            }

            @Override
            public boolean isSingleton() {
                return true;
            }
        };
        endpoint.setCamelContext(camelContext);
        Consumer consumer = new DefaultConsumer(endpoint, processor) {
        };
        registrations.add(new Registration(verb, basePath, consumes, produces, processor));
        return consumer;
    }

    List<Registration> registrations() {
        return registrations;
    }

    /** Calls the registered processor with the request, as the HTTP server does. */
    void dispatch(Exchange request) throws Exception {
        if (registrations.size() != 1) {
            throw new IllegalStateException("Expected one registered route, found " + registrations.size());
        }
        registrations.get(0).processor().process(request);
    }

    record Registration(String verb, String path, String consumes, String produces, Processor processor) {
    }
}
