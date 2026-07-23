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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;

final class NoopRestConsumerFactory implements RestConsumerFactory {

    private final List<Registration> registrations = new ArrayList<>();

    static NoopRestConsumerFactory bindTo(CamelContext context) {
        NoopRestConsumerFactory factory = new NoopRestConsumerFactory();
        context.getRegistry().bind("noopRestConsumerFactory", factory);
        return factory;
    }

    @Override
    public Consumer createConsumer(
            CamelContext camelContext, Processor processor, String verb, String basePath,
            String uriTemplate, String consumes, String produces, RestConfiguration configuration,
            Map<String, Object> parameters)
            throws Exception {
        DefaultEndpoint endpoint = new DefaultEndpoint("noop-rest:" + registrations.size(), null) {
            @Override
            public Producer createProducer() {
                throw new UnsupportedOperationException("Noop REST endpoint is consumer-only");
            }

            @Override
            public Consumer createConsumer(Processor processor) {
                throw new UnsupportedOperationException("Noop REST consumers are created by the factory");
            }

            @Override
            public boolean isSingleton() {
                return true;
            }
        };
        endpoint.setCamelContext(camelContext);
        Consumer consumer = new DefaultConsumer(endpoint, processor) {
        };
        registrations.add(new Registration(verb, basePath, consumes, produces, parameters, consumer));
        return consumer;
    }

    List<Registration> registrations() {
        return registrations;
    }

    static final class Registration {
        private final String verb;
        private final String path;
        private final String consumes;
        private final String produces;
        private final Map<String, Object> parameters;
        private final Consumer consumer;

        Registration(
                     String verb, String path, String consumes, String produces, Map<String, Object> parameters,
                     Consumer consumer) {
            this.verb = verb;
            this.path = path;
            this.consumes = consumes;
            this.produces = produces;
            this.parameters = parameters;
            this.consumer = consumer;
        }

        String verb() {
            return verb;
        }

        String path() {
            return path;
        }

        String consumes() {
            return consumes;
        }

        String produces() {
            return produces;
        }

        Map<String, Object> parameters() {
            return parameters;
        }

        Consumer consumer() {
            return consumer;
        }
    }
}
