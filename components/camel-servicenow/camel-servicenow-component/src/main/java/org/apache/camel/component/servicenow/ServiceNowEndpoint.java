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
package org.apache.camel.component.servicenow;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The servicenow component is used to integrate Camel with <a href="http://www.servicenow.com/">ServiceNow</a> cloud services.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "servicenow", title = "ServiceNow", syntax = "servicenow:instanceName", producerOnly = true, label = "api,cloud,management")
public class ServiceNowEndpoint extends DefaultEndpoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceNowEndpoint.class);

    @UriPath(description = "The ServiceNow instance name")
    @Metadata(required = "true")
    private final String instanceName;

    @UriParam
    private final ServiceNowConfiguration configuration;

    public ServiceNowEndpoint(String uri, ServiceNowComponent component, ServiceNowConfiguration configuration, String instanceName) throws Exception {
        super(uri, component);

        this.configuration = configuration;
        this.instanceName = instanceName;
    }

    @Override
    public Producer createProducer() throws Exception {
        ServiceNowProducer producer = configuration.getRelease().get(this);
        LOGGER.info("Producer for ServiceNow Rel. {} = {}/{}",
            configuration.getRelease().name(),
            producer.getRelease().name(),
            producer.getClass().getName()
        );

        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer is not supported");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public ServiceNowConfiguration getConfiguration() {
        return configuration;
    }

    public String getInstanceName() {
        return instanceName;
    }
}
