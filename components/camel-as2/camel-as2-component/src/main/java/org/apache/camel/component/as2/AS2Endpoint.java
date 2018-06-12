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
package org.apache.camel.component.as2;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2ClientManager;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.internal.AS2ApiCollection;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.component.as2.internal.AS2ConnectionHelper;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.component.as2.internal.AS2PropertiesHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * Component used for transferring data secure and reliable over the internet using the AS2 protocol.
 */
@UriEndpoint(scheme = "as2", firstVersion = "2.22.0", title = "AS2", syntax = "as2:name", consumerClass = AS2Consumer.class, label = "AS2")
public class AS2Endpoint extends AbstractApiEndpoint<AS2ApiName, AS2Configuration> {

    @UriPath @Metadata(required = "true")
    private String name;

    private Object apiProxy;

    private AS2ClientConnection as2ClientConnection;

    private AS2ServerConnection as2ServerConnection;

    public AS2Endpoint(String uri, AS2Component component,
                         AS2ApiName apiName, String methodName, AS2Configuration endpointConfiguration) {
        super(uri, component, apiName, methodName, AS2ApiCollection.getCollection().getHelper(apiName), endpointConfiguration);

    }

    public AS2ClientConnection getAS2ClientConnection() {
        return as2ClientConnection;
    }

    public AS2ServerConnection getAS2ServerConnection() {
        return as2ServerConnection;
    }

    public Producer createProducer() throws Exception {
        return new AS2Producer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final AS2Consumer consumer = new AS2Consumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<AS2Configuration> getPropertiesHelper() {
        return AS2PropertiesHelper.getHelper();
    }

    protected String getThreadProfileName() {
        return AS2Constants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        // create HTTP connection eagerly, a good way to validate configuration
        switch (apiName) {
        case SEND:
            createAS2ClientConnection();
            break;
        case LISTEN:
            createAS2ServerConnection();
            break;
        default:
            break;
        }
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        if (apiProxy == null) {
            createApiProxy(method, args);
        }
        return apiProxy;
    }

    /**
     * Some description of this option, and what it does
     */
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private void createApiProxy(ApiMethod method, Map<String, Object> args) {
        switch (apiName) {
        case SEND:
            apiProxy = new AS2ClientManager(getAS2ClientConnection());
            break;
        case LISTEN:
            apiProxy = new AS2ServerManager(getAS2ServerConnection());
            break;
        default:
            throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }

    private void createAS2ClientConnection() {
        try {
            as2ClientConnection = AS2ConnectionHelper.createAS2ClientConnection(configuration);
        } catch (UnknownHostException e) {
            throw new RuntimeCamelException(String.format("Client HTTP connection failed: Unknown target host '%s'",
                    configuration.getTargetHostname()));
        } catch (IOException e) {
            throw new RuntimeCamelException("Client HTTP connection failed", e);
        }
    }

    private void createAS2ServerConnection() {
        try {
            as2ServerConnection = AS2ConnectionHelper.createAS2ServerConnection(configuration);
        } catch (IOException e) {
            throw new RuntimeCamelException("Server HTTP connection failed", e);
        }
    }

}
