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
package org.apache.camel.component.ironmq;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link IronMQEndpoint}.
 */
public class IronMQComponent extends UriEndpointComponent {

    public IronMQComponent(CamelContext context) {
        super(context, IronMQEndpoint.class);
    }

    public IronMQComponent() {
        super(IronMQEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        IronMQConfiguration ironMQConfiguration = new IronMQConfiguration();
        setProperties(ironMQConfiguration, parameters);
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Queue name must be specified.");
        }

        ironMQConfiguration.setQueueName(remaining);

        if (ironMQConfiguration.getClient() == null && (ironMQConfiguration.getProjectId() == null || ironMQConfiguration.getToken() == null)) {
            throw new IllegalArgumentException("Client or project and token must be specified.");
        }

        Endpoint endpoint = new IronMQEndpoint(uri, this, ironMQConfiguration);
        ((ScheduledPollEndpoint)endpoint).setConsumerProperties(parameters);

        return endpoint;
    }
}
