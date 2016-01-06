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
package org.apache.camel.component.jmx;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Component for connecting JMX Notification events to a camel route.
 * The endpoint created from this component allows users to specify
 * an ObjectName to listen to and any JMX Notifications received from
 * that object will flow into the route.
 */
public class JMXComponent extends UriEndpointComponent {

    public JMXComponent() {
        super(JMXEndpoint.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        JMXEndpoint endpoint = new JMXEndpoint(uri, this);
        // use the helper class to set all of the properties
        EndpointHelper.setReferenceProperties(getCamelContext(), endpoint, parameters);
        EndpointHelper.setProperties(getCamelContext(), endpoint, parameters);

        endpoint.setServerURL(remaining);

        Map objectProperties = IntrospectionSupport.extractProperties(parameters, "key.");
        if (objectProperties != null && !objectProperties.isEmpty()) {
            endpoint.setObjectProperties(objectProperties);
        }

        if (endpoint.getObjectDomain() == null) {
            throw new IllegalArgumentException("Must specify domain");
        }

        if (endpoint.getObjectName() == null && endpoint.getObjectProperties() == null) {
            throw new IllegalArgumentException("Must specify object name or object properties");
        }

        return endpoint;
    }

}
