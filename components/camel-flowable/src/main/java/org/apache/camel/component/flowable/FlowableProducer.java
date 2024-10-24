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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.flowable.eventregistry.impl.DefaultInboundEvent;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.flowable.eventregistry.model.CamelInboundChannelModel;

public class FlowableProducer extends DefaultProducer {

    protected CamelInboundChannelModel camelInboundChannel;
    protected EventRegistryEngineConfiguration eventRegistryEngineConfiguration;

    public FlowableProducer(CamelInboundChannelModel camelInboundChannel, Endpoint endpoint,
                            EventRegistryEngineConfiguration eventRegistryEngineConfiguration) {

        super(endpoint);
        this.camelInboundChannel = camelInboundChannel;
        this.eventRegistryEngineConfiguration = eventRegistryEngineConfiguration;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getMessage();
        DefaultInboundEvent inboundEvent = new DefaultInboundEvent(message.getBody(), message.getHeaders());
        eventRegistryEngineConfiguration.getEventRegistry().eventReceived(camelInboundChannel, inboundEvent);
    }
}
