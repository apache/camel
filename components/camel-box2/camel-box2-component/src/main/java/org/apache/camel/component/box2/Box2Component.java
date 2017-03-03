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
package org.apache.camel.component.box2;

import com.box.sdk.BoxAPIConnection;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.box2.internal.Box2ApiCollection;
import org.apache.camel.component.box2.internal.Box2ApiName;
import org.apache.camel.component.box2.internal.Box2ConnectionHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link Box2Endpoint}.
 * 
 * 
 * 
 */
// @UriEndpoint(scheme = "box2", consumerClass = Box2Consumer.class,
// consumerPrefix = "consumer", syntax = "", title = "Box2 Component")
public class Box2Component extends AbstractApiComponent<Box2ApiName, Box2Configuration, Box2ApiCollection> {

    @Metadata(label = "advanced")
    BoxAPIConnection boxConnection;

    public Box2Component() {
        super(Box2Endpoint.class, Box2ApiName.class, Box2ApiCollection.getCollection());
    }

    public Box2Component(CamelContext context) {
        super(context, Box2Endpoint.class, Box2ApiName.class, Box2ApiCollection.getCollection());
    }

    @Override
    protected Box2ApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return Box2ApiName.fromValue(apiNameStr);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(Box2Configuration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public Box2Configuration getConfiguration() {
        return super.getConfiguration();
    }

    /**
     * To use a shared connection
     * 
     * @return the shared connection
     */
    public BoxAPIConnection getBoxConnection() {
        return boxConnection;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, Box2ApiName apiName,
            Box2Configuration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new Box2Endpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (boxConnection == null) {
            if (configuration != null) {
                boxConnection = Box2ConnectionHelper.createConnection(configuration);
            } else {
                throw new IllegalArgumentException("Unable to connect, Box2 component configuration is missing");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (boxConnection != null) {
            boxConnection = null;
        }
    }

    @Override
    public void doShutdown() throws Exception {
        if (boxConnection != null) {
            boxConnection = null;
        }
        super.doShutdown();
    }
}
