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
package org.apache.camel.component.webhook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;

/**
 * MultiRestConsumer allows to bind the webhook to multiple local rest endpoints.
 * It is useful for services that need to respond to multiple kinds of requests.
 * <p>
 * E.g. some webhook providers operate over POST but they do require that a specific endpoint replies also to
 * GET requests during handshake.
 */
public class MultiRestConsumer extends DefaultConsumer {

    private List<Consumer> delegateConsumers;

    public MultiRestConsumer(CamelContext context, RestConsumerFactory factory, Endpoint endpoint, Processor processor,
                             List<String> methods, String url, String path, RestConfiguration config, ConsumerConfigurer configurer) throws Exception {
        super(endpoint, processor);
        this.delegateConsumers = new ArrayList<>();
        for (String method : methods) {
            Consumer consumer = factory.createConsumer(context, processor, method, path,
                    null, null, null, config, Collections.emptyMap());
            configurer.configure(consumer);

            context.getRestRegistry().addRestService(consumer, url, url, path, null, method,
                    null, null, null, null, null, null);

            this.delegateConsumers.add(consumer);
        }
    }

    @Override
    protected void doInit() {
        for (Consumer consumer : this.delegateConsumers) {
            consumer.init();
        }
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        for (Consumer consumer : this.delegateConsumers) {
            ServiceHelper.startService(consumer);
        }
    }

    @Override
    public void doStop() throws Exception {
        for (Consumer consumer : this.delegateConsumers) {
            ServiceHelper.stopService(consumer);
        }
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        for (Consumer consumer : this.delegateConsumers) {
            ServiceHelper.stopAndShutdownService(consumer);
        }
        super.doShutdown();
    }

    @FunctionalInterface
    interface ConsumerConfigurer {
        void configure(Consumer consumer) throws Exception;
    }
}
