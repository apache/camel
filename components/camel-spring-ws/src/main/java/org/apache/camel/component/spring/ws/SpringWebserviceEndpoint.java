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
package org.apache.camel.component.spring.ws;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Access external web services as a client or expose your own web services.
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "spring-ws", title = "Spring WebService",
             syntax = "spring-ws:type:lookupKey:webServiceEndpointUri",
             category = { Category.WEBSERVICE }, headersClass = SpringWebserviceConstants.class)
public class SpringWebserviceEndpoint extends DefaultEndpoint {

    @UriParam
    private SpringWebserviceConfiguration configuration;

    public SpringWebserviceEndpoint(Component component, String uri, SpringWebserviceConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SpringWebserviceConsumer consumer = new SpringWebserviceConsumer(this, processor);
        if (configuration.getEndpointDispatcher() != null) {
            configuration.getEndpointDispatcher().setConsumerMessageEndpoint(consumer);
        }
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringWebserviceProducer(this);
    }

    public SpringWebserviceConfiguration getConfiguration() {
        return configuration;
    }

}
