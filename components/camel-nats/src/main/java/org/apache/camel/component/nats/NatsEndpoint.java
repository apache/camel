/**
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
package org.apache.camel.component.nats;

import java.util.concurrent.ExecutorService;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

@UriEndpoint(scheme = "nats", title = "Nats", syntax = "nats:servers", label = "messaging", consumerClass = NatsConsumer.class)
public class NatsEndpoint extends DefaultEndpoint {

    @UriParam
    private NatsConfiguration configuration;
    
    public NatsEndpoint(String uri, NatsComponent component, NatsConfiguration config) {
        super(uri, component);
        this.configuration = config;
    }    
    
    @Override
    public Producer createProducer() throws Exception {
        return new NatsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new NatsConsumer(this, processor);
    }
    
    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "NatsTopic[" + configuration.getTopic() + "]", configuration.getPoolSize());
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
    
    public NatsConfiguration getNatsConfiguration() {
        return configuration;
    }
}
