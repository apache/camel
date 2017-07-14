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
package org.apache.camel.component.google.pubsub;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link GooglePubsubEndpoint}.
 */
public class GooglePubsubComponent extends UriEndpointComponent {

    private GooglePubsubConnectionFactory connectionFactory;

    public GooglePubsubComponent() {
        super(GooglePubsubEndpoint.class);
    }

    public GooglePubsubComponent(CamelContext context) {
        super(context, GooglePubsubEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        String[] parts = remaining.split(":");

        if (parts.length < 2) {
            throw new IllegalArgumentException("Google PubSub Endpoint format \"projectId:destinationName[:subscriptionName]\"");
        }

        GooglePubsubEndpoint pubsubEndpoint = new GooglePubsubEndpoint(uri, this, remaining);
        pubsubEndpoint.setProjectId(parts[0]);
        pubsubEndpoint.setDestinationName(parts[1]);

        setProperties(pubsubEndpoint, parameters);

        return pubsubEndpoint;
    }

    /**
     * Sets the connection factory to use:
     * provides the ability to explicitly manage connection credentials:
     * - the path to the key file
     * - the Service Account Key / Email pair
     */
    public GooglePubsubConnectionFactory getConnectionFactory() {
        if (connectionFactory == null) {
            connectionFactory = new GooglePubsubConnectionFactory();
        }
        return connectionFactory;
    }

    public void setConnectionFactory(GooglePubsubConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }
}

