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
import org.apache.camel.processor.Pipeline;
import org.apache.camel.util.ServiceHelper;

@ManagedResource(description = "Managed Connector Endpoint")
public class DefaultConnectorEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private final Endpoint endpoint;
    private final DataType inputDataType;
    private final DataType outputDataType;
    private Processor beforeProducer;
    private Processor afterProducer;
    private Processor beforeConsumer;
    private Processor afterConsumer;

    public DefaultConnectorEndpoint(String endpointUri, ConnectorComponent component, Endpoint endpoint,
                                    DataType inputDataType, DataType outputDataType) {
        super(endpointUri, component);
        this.endpoint = endpoint;
        this.inputDataType = inputDataType;
        this.outputDataType = outputDataType;
    }

    @Override
    public Producer createProducer() throws Exception {
        final Producer producer = endpoint.createProducer();

        final Processor beforeProducer = getBeforeProducer();
        final Processor afterProducer = getAfterProducer();

        // use a pipeline to process before, producer, after in that order
        // create producer with the pipeline
        final Processor pipeline = Pipeline.newInstance(getCamelContext(), beforeProducer, producer, afterProducer);

        return new ConnectorProducer(endpoint, pipeline);
    }

    @Override
    public Consumer createConsumer(final Processor processor) throws Exception {
        final Processor beforeConsumer = getBeforeConsumer();
        final Processor afterConsumer = getAfterConsumer();

        // use a pipeline to process before, processor, after in that order
        // create consumer with the pipeline
        final Processor pipeline = Pipeline.newInstance(getCamelContext(), beforeConsumer, processor, afterConsumer);
        final Consumer consumer = endpoint.createConsumer(pipeline);
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

    /**
     * Gets the processor used to perform custom processing before the producer is sending the message.
     */
    public Processor getBeforeProducer() {
        return beforeProducer;
    }

    /**
     * To perform custom processing before the producer is sending the message.
     */
    public void setBeforeProducer(Processor beforeProducer) {
        this.beforeProducer = beforeProducer;
    }

    /**
     * Gets the processor used to perform custom processing after the producer has sent the message and received any reply (if InOut).
     */
    public Processor getAfterProducer() {
        return afterProducer;
    }

    /**
     * To perform custom processing after the producer has sent the message and received any reply (if InOut).
     */
    public void setAfterProducer(Processor afterProducer) {
        this.afterProducer = afterProducer;
    }

    /**
     * Gets the processor used to perform custom processing when the consumer has just received a new incoming message.
     */
    public Processor getBeforeConsumer() {
        return beforeConsumer;
    }

    /**
     * To perform custom processing when the consumer has just received a new incoming message.
     */
    public void setBeforeConsumer(Processor beforeConsumer) {
        this.beforeConsumer = beforeConsumer;
    }

    /**
     * Gets the processor used to perform custom processing when the consumer is about to send back a reply message to the caller (if InOut).
     */
    public Processor getAfterConsumer() {
        return afterConsumer;
    }

    /**
     * To perform custom processing when the consumer is about to send back a reply message to the caller (if InOut).
     */
    public void setAfterConsumer(Processor afterConsumer) {
        this.afterConsumer = afterConsumer;
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
