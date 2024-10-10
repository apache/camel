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
package org.apache.camel.component.flowable;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultEndpoint;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.flowable.eventregistry.model.CamelInboundChannelModel;
import org.flowable.eventregistry.model.CamelOutboundChannelModel;

public class FlowableEndpoint extends DefaultEndpoint {

    protected EventRegistryEngineConfiguration eventRegistryEngineConfiguration;

    protected FlowableConsumer flowableConsumer;
    protected CamelInboundChannelModel camelInboundChannel;
    protected CamelOutboundChannelModel camelOutboundChannel;

    public FlowableEndpoint(CamelInboundChannelModel camelInboundChannel,
                            EventRegistryEngineConfiguration eventRegistryEngineConfiguration, CamelContext camelContext) {

        super();
        setCamelContext(camelContext);
        this.camelInboundChannel = camelInboundChannel;
        this.eventRegistryEngineConfiguration = eventRegistryEngineConfiguration;
        setEndpointUri("flowable:" + camelInboundChannel.getKey());
    }

    public FlowableEndpoint(CamelOutboundChannelModel camelOutboundChannel,
                            EventRegistryEngineConfiguration eventRegistryEngineConfiguration, CamelContext camelContext) {

        super();
        setCamelContext(camelContext);
        this.camelOutboundChannel = camelOutboundChannel;
        this.eventRegistryEngineConfiguration = eventRegistryEngineConfiguration;
        setEndpointUri("flowable-event:" + camelOutboundChannel.getKey());
    }

    public void process(Exchange ex) throws Exception {
        if (flowableConsumer == null) {
            throw new FlowableException("Consumer not defined for " + getEndpointUri());
        }
        flowableConsumer.getProcessor().process(ex);
    }

    @Override
    public Producer createProducer() throws Exception {
        FlowableProducer producer = new FlowableProducer(camelInboundChannel, this, eventRegistryEngineConfiguration);
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new FlowableConsumer(this, processor);
    }

    protected void addConsumer(FlowableConsumer consumer) {
        if (flowableConsumer != null) {
            throw new FlowableException("Consumer already defined for " + getEndpointUri() + "!");
        }
        flowableConsumer = consumer;
    }

    protected void removeConsumer() {
        flowableConsumer = null;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setCamelInboundChannel(CamelInboundChannelModel camelInboundChannel) {
        this.camelInboundChannel = camelInboundChannel;
    }

    public void setCamelOutboundChannel(CamelOutboundChannelModel camelOutboundChannel) {
        this.camelOutboundChannel = camelOutboundChannel;
    }

    public void setEventRegistryEngineConfiguration(EventRegistryEngineConfiguration eventRegistryEngineConfiguration) {
        this.eventRegistryEngineConfiguration = eventRegistryEngineConfiguration;
    }
}
