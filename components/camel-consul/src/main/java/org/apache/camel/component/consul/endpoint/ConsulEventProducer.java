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
package org.apache.camel.component.consul.endpoint;

import org.apache.camel.Message;
import org.apache.camel.component.consul.ConsulConfiguration;
import org.apache.camel.component.consul.ConsulConstants;
import org.apache.camel.component.consul.ConsulEndpoint;
import org.apache.camel.spi.InvokeOnHeader;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.EventClient;
import org.kiwiproject.consul.option.EventOptions;
import org.kiwiproject.consul.option.QueryOptions;

public final class ConsulEventProducer extends AbstractConsulProducer<EventClient> {

    public ConsulEventProducer(ConsulEndpoint endpoint, ConsulConfiguration configuration) {
        super(endpoint, configuration, Consul::eventClient);
    }

    @InvokeOnHeader(ConsulEventActions.FIRE)
    protected void fire(Message message) throws Exception {
        setBodyAndResult(message,
                getClient().fireEvent(
                        getMandatoryHeader(message, ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class),
                        message.getHeader(ConsulConstants.CONSUL_OPTIONS, EventOptions.BLANK, EventOptions.class),
                        message.getBody(String.class)));
    }

    @InvokeOnHeader(ConsulEventActions.LIST)
    protected void list(Message message) throws Exception {
        setBodyAndResult(message,
                getClient().listEvents(message.getHeader(ConsulConstants.CONSUL_KEY, getConfiguration().getKey(), String.class),
                        message.getHeader(ConsulConstants.CONSUL_OPTIONS, QueryOptions.BLANK, QueryOptions.class)));
    }
}
