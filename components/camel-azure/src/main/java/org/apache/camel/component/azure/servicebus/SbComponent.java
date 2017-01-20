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

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     *
     * @param uri full URI (e.g. azure-sb://queue or azure-sb://<sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/topic)
     * @param remaining URI without the location part (e.g. <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/queue)
     * @param parameters URI parameters passed in (e.g. queueName, timeout, peekLock)
     * @return a Camel endpoint for Azure Service Bus
     */
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {

        SbConfiguration configuration = parseRemaining(remaining);

        try {
            setProperties(configuration, parameters);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to set Camel properties from passed in parameters", e);
        }

        boolean noClient = configuration.getServiceBusContract() == null;
        boolean noCredentials = isBlank(configuration.getSasKeyName()) || isBlank(configuration.getSasKey());
        boolean noNamespace = isBlank(configuration.getNamespace()) || isBlank(configuration.getServiceBusRootUri());

        if (noClient || (noCredentials && noNamespace)) {
            throw new IllegalArgumentException("serviceBusContract or sasKey, sasKeyName, serviceBusRootUri and namespace must be present.");
        }

        AbstractSbEndpoint endpoint;
        switch (configuration.getEntities()) {
        case QUEUE:
            endpoint = new SbQueueEndpoint(uri, this, configuration);
            break;
        case TOPIC:
            endpoint = new SbTopicEndpoint(uri, this, configuration);
            break;
        case EVENT:
            endpoint = new SbEventEndpoint(uri, this, configuration);
            break;
        default:
            throw new IllegalArgumentException("Bad entities chanel.");
        }

        endpoint.setConsumerProperties(parameters);
        return endpoint;
    }

    static SbConfiguration parseRemaining(String remaining) {
        SbConfiguration configuration = new SbConfiguration();

        Pattern pattern = Pattern.compile("(.+):(.+)@(.+)/(.+)");
        Matcher matcher = pattern.matcher(remaining);

        if (defaultString(remaining).toUpperCase().matches("QUEUE|TOPIC|EVENT")) {
            configuration.setEntities(SbConstants.EntityType.valueOf(remaining.toUpperCase()));
        } else if (matcher.find()) {
            configuration.setSasKeyName(matcher.group(1).trim());
            configuration.setSasKey(matcher.group(2).trim());

            String asbHost = matcher.group(3).trim();
            String[] asbHostParts = asbHost.split("\\.", 2);
            if (asbHostParts.length > 1) {
                configuration.setNamespace(asbHostParts[0].trim());
                configuration.setServiceBusRootUri("." + asbHostParts[1]);
            } else {
                configuration.setNamespace(asbHost);
            }

            SbConstants.EntityType entity = SbConstants.EntityType.valueOf(matcher.group(4).toUpperCase());
            configuration.setEntities(entity);
        } else {
            String message = String.format("valid enpoint formats are:%n  <entity>?<parameter1=value1>&<parameter2=value>%n  <sasKeyName>:<sasKey>@<namespace>.<serviceBusRootUri>/<entity>?<parameter1=value1>&<parameter2=value>");
            throw new IllegalArgumentException(message);
        }

        return configuration;
    }
}
