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
package org.apache.camel.component.mllp;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link MllpEndpoint}.
 */
public class MllpComponent extends UriEndpointComponent {
    public static final String MLLP_LOG_PHI_PROPERTY = "org.apache.camel.component.mllp.logPHI";

    public MllpComponent() {
        super(MllpEndpoint.class);
    }

    public MllpComponent(CamelContext context) {
        super(context, MllpEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MllpEndpoint endpoint = new MllpEndpoint(uri, this);
        setProperties(endpoint, parameters);

        // mllp://hostname:port
        String hostPort;
        // look for options
        int optionsStartIndex = uri.indexOf('?');
        if (-1 == optionsStartIndex) {
            // No options - just get the host/port stuff
            hostPort = uri.substring(7);
        } else {
            hostPort = uri.substring(7, optionsStartIndex);
        }

        // Make sure it has a host - may just be a port
        int colonIndex = hostPort.indexOf(':');
        if (-1 != colonIndex) {
            endpoint.setHostname(hostPort.substring(0, colonIndex));
            endpoint.setPort(Integer.parseInt(hostPort.substring(colonIndex + 1)));
        } else {
            // No host specified - leave the default host and set the port
            endpoint.setPort(Integer.parseInt(hostPort.substring(colonIndex + 1)));
        }

        return endpoint;
    }

}
