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
package org.apache.camel.component.box;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.component.box.internal.BoxClientHelper;
import org.apache.camel.component.box.internal.CachedBoxClient;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link BoxEndpoint}.
 */
public class BoxComponent extends AbstractApiComponent<BoxApiName, BoxConfiguration, BoxApiCollection> {

    private CachedBoxClient cachedBoxClient;

    public BoxComponent() {
        super(BoxEndpoint.class, BoxApiName.class, BoxApiCollection.getCollection());
    }

    public BoxComponent(CamelContext context) {
        super(context, BoxEndpoint.class, BoxApiName.class, BoxApiCollection.getCollection());
    }

    @Override
    protected BoxApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return BoxApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, BoxApiName apiName,
                                      BoxConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new BoxEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    public CachedBoxClient getBoxClient() {
        return cachedBoxClient;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(BoxConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (cachedBoxClient == null) {
            if (configuration != null) {
                cachedBoxClient = BoxClientHelper.createBoxClient(configuration);
            } else {
                throw new IllegalArgumentException("Unable to connect, Box component configuration is missing");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (cachedBoxClient != null) {
            // close shared client connections
            BoxClientHelper.closeIdleConnections(cachedBoxClient);
        }
    }

    @Override
    public void doShutdown() throws Exception {
        try {
            if (cachedBoxClient != null) {
                // shutdown singleton client
                BoxClientHelper.shutdownBoxClient(configuration, cachedBoxClient);
            }
        } finally {
            cachedBoxClient = null;
            super.doShutdown();
        }
    }
}
