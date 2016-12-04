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
package org.apache.camel.component.azure.servicebus;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

public class SbComponent extends UriEndpointComponent {

    public SbComponent() {
        super(AbstractSbEndpoint.class);
    }

    public SbComponent(CamelContext context) {
        super(context, AbstractSbEndpoint.class);
    }

    private EntityType toEntityType(String s) {
        switch (s) {
        case "queue":
            return EntityType.QUEUE;
        case "topic":
            return EntityType.TOPIC;
        case "event":
            return EntityType.EVENT;
        default:
            throw new IllegalArgumentException("Entities type should be: queue/topic/event.");
        }
    }

    /**
     * Usage:
     *
     *   azure-sb://queue?queueName=MyQueue&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true
     *   azure-sb://<sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/<queue>?queueName=<queueName>&timeout=<timeout>&peekLock=<peekLock>
     *
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SbConfiguration configuration = new SbConfiguration();
        setProperties(configuration, parameters);
        //"azure-sb://queue?queueName=MyQueue&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true
        // azure-sb://<sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/<queue>?queueName=<queueName>&timeout=<timeout>&peekLock=<peekLock>
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Entities must be specified.");
        }

        if (remaining.contains("/")) {
            if (remaining.contains("@")) {
                String[] siteParts = remaining.split("@");
                String[] parts = siteParts[1].split("/");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("1.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/<entities>.");
                }
                if (siteParts.length != 2) {
                    throw new IllegalArgumentException("2.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
                }
                String[] sasParts = siteParts[0].split(":");
                if (sasParts.length != 2) {
                    throw new IllegalArgumentException("3.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
                }
                configuration.setEntities(toEntityType(parts[1]));

                configuration.setSasKeyName(sasParts[0]);
                configuration.setSasKey(sasParts[1]);
                String[] domainParts = parts[0].split("\\.");
                if (domainParts.length < 2) {
                    throw new IllegalArgumentException("4.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
                }
                configuration.setNamespace(domainParts[0]);
                configuration.setServiceBusRootUri(parts[0].substring(domainParts[0].length()));

            } else {
                throw new IllegalArgumentException("5.Endpoint must be in format <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>.");
            }
        } else {
            configuration.setEntities(toEntityType(remaining));
        }

        if (configuration.getServiceBusContract() == null && (
                configuration.getSasKey() == null
                        || configuration.getSasKeyName() == null
                        || configuration.getServiceBusRootUri() == null
                        || configuration.getNamespace() == null)) {
            throw new IllegalArgumentException("serviceBusContract or sasKey, sasKeyName, serviceBusRootUri and namespace must be specified.");
        }

        AbstractSbEndpoint abstractSbEndpoint;
        switch (configuration.getEntities()) {
        case QUEUE:
            abstractSbEndpoint = new SbQueueEndpoint(uri, this, configuration);
            break;
        case TOPIC:
            abstractSbEndpoint = new SbTopicEndpoint(uri, this, configuration);
            break;
        case EVENT:
            abstractSbEndpoint = new SbEventEndpoint(uri, this, configuration);
            break;
        default:
            throw new Exception("Bad entities chanel.");
        }
        abstractSbEndpoint.setConsumerProperties(parameters);
        return abstractSbEndpoint;
    }
}
