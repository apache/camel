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
package org.apache.camel.component.connector;

import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ServiceHelper;

@ManagedResource(description = "Managed Connector Endpoint")
public class DefaultConnectorEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private final Endpoint endpoint;
    private final DataType inputDataType;
    private final DataType outputDataType;

    public DefaultConnectorEndpoint(String endpointUri, ConnectorComponent component, Endpoint endpoint,
                                    DataType inputDataType, DataType outputDataType) {
        super(endpointUri, component);
        this.endpoint = endpoint;
        this.inputDataType = inputDataType;
        this.outputDataType = outputDataType;
    }

    @Override
    public Producer createProducer() throws Exception {
        Producer producer = endpoint.createProducer();
        return new ConnectorProducer(endpoint, producer, getComponent().getBeforeProducer(), getComponent().getAfterProducer());
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ConnectorConsumerProcessor delegate = new ConnectorConsumerProcessor(processor, getComponent().getBeforeConsumer(), getComponent().getAfterConsumer());
        Consumer consumer = endpoint.createConsumer(delegate);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public ConnectorComponent getComponent() {
        return (ConnectorComponent) super.getComponent();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @ManagedAttribute(description = "Delegate Endpoint URI", mask = true)
    public String getDelegateEndpointUri() {
        return endpoint.getEndpointUri();
    }

    @ManagedAttribute(description = "Input data type")
    public DataType getInputDataType() {
        return inputDataType;
    }

    @ManagedAttribute(description = "Output data type")
    public DataType getOutputDataType() {
        return outputDataType;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(endpoint);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(endpoint);
        super.doStop();
    }
}
