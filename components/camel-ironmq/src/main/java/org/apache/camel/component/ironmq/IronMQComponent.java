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
package org.apache.camel.component.ironmq;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

/**
 * Represents the component that manages {@link IronMQEndpoint}.
 */
@Component("ironmq")
public class IronMQComponent extends HealthCheckComponent {

    public IronMQComponent(CamelContext context) {
        super(context);
    }

    public IronMQComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Queue name must be specified.");
        }

        IronMQConfiguration ironMQConfiguration = new IronMQConfiguration();
        Endpoint endpoint = new IronMQEndpoint(uri, this, ironMQConfiguration);
        ironMQConfiguration.setQueueName(remaining);
        setProperties(endpoint, parameters);
        if (ironMQConfiguration.getClient() == null
                && (ironMQConfiguration.getProjectId() == null || ironMQConfiguration.getToken() == null)) {
            throw new IllegalArgumentException("Client or project and token must be specified.");
        }

        return endpoint;
    }
}
