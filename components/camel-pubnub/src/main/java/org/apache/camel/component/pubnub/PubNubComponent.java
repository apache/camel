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
package org.apache.camel.component.pubnub;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link PubNubEndpoint}.
 */
public class PubNubComponent extends DefaultComponent {

    public PubNubComponent(CamelContext context) {
        super(context);
    }

    public PubNubComponent() {
        super();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String[] uriParts = remaining.split(":");
        if (uriParts.length != 2) {
            throw new IllegalArgumentException("Invalid Endpoint URI: " + uri + ". It should contains a valid endpointType and channel");
        }
        PubNubEndpointType endpointType = PubNubEndpointType.valueOf(uriParts[0]);
        String channel = uriParts[1];

        PubNubEndpoint endpoint = new PubNubEndpoint(uri, this);
        setProperties(endpoint, parameters);
        endpoint.setEndpointType(endpointType);
        endpoint.setChannel(channel);
        return endpoint;
    }

}
