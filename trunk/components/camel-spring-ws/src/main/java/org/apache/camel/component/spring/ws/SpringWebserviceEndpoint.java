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
package org.apache.camel.component.spring.ws;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

public class SpringWebserviceEndpoint extends DefaultEndpoint {

    private SpringWebserviceConfiguration configuration;

    public SpringWebserviceEndpoint(Component component, SpringWebserviceConfiguration configuration) {
        super(configuration.getEndpointUri(), component);
        this.configuration = configuration;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        SpringWebserviceConsumer consumer = new SpringWebserviceConsumer(this, processor);
        if (configuration.getEndpointDispatcher() != null) {
            configuration.getEndpointDispatcher().setConsumerMessageEndpoint(consumer);
        }
        return consumer;
    }

    public Producer createProducer() throws Exception {
        return new SpringWebserviceProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    public SpringWebserviceConfiguration getConfiguration() {
        return configuration;
    }

}
