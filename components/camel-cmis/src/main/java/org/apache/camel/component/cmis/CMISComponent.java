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
package org.apache.camel.component.cmis;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Represents the component that manages {@link CMISComponent}.
 */
public class CMISComponent extends UriEndpointComponent {

    private CMISSessionFacadeFactory sessionFacadeFactory;

    public CMISComponent() {
        super(CMISEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        CMISEndpoint endpoint = new CMISEndpoint(uri, this, remaining);

        // create a copy of parameters which we need to store on the endpoint which are in use from the session factory
        Map<String, Object> copy = new HashMap<>(parameters);
        endpoint.setProperties(copy);
        if (sessionFacadeFactory != null) {
            endpoint.setSessionFacadeFactory(sessionFacadeFactory);
        }

        // create a dummy CMISSessionFacade which we set the properties on
        // so we can validate if they are all known options and fail fast if there are unknown options
        CMISSessionFacade dummy = new CMISSessionFacade(remaining);
        setProperties(dummy, parameters);

        // and the remainder options are for the endpoint
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public CMISSessionFacadeFactory getSessionFacadeFactory() {
        return sessionFacadeFactory;
    }

    /**
     * To use a custom CMISSessionFacadeFactory to create the CMISSessionFacade instances
     */
    public void setSessionFacadeFactory(CMISSessionFacadeFactory sessionFacadeFactory) {
        this.sessionFacadeFactory = sessionFacadeFactory;
    }
}
