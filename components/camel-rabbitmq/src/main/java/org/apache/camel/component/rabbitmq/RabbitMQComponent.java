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
package org.apache.camel.component.rabbitmq;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQComponent extends DefaultComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(RabbitMQComponent.class);

    public RabbitMQComponent() {
    }

    public RabbitMQComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected RabbitMQEndpoint createEndpoint(String uri,
                                              String remaining,
                                              Map<String, Object> params) throws Exception {
        URI host = new URI("http://" + remaining);
        String hostname = host.getHost();
        int portNumber = host.getPort();
        String exchangeName = host.getPath().substring(1);

        RabbitMQEndpoint endpoint = new RabbitMQEndpoint(uri, this);
        endpoint.setHostname(hostname);
        endpoint.setPortNumber(portNumber);
        endpoint.setExchangeName(exchangeName);

        setProperties(endpoint, params);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating RabbitMQEndpoint with host {}:{} and exchangeName: {}",
                    new Object[]{endpoint.getHostname(), endpoint.getPortNumber(), endpoint.getExchangeName()});
        }

        return endpoint;
    }
}
