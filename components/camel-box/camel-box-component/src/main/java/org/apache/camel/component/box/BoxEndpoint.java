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

import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.box.api.BoxCollaborationsManager;
import org.apache.camel.component.box.api.BoxCommentsManager;
import org.apache.camel.component.box.api.BoxEventLogsManager;
import org.apache.camel.component.box.api.BoxEventsManager;
import org.apache.camel.component.box.api.BoxFilesManager;
import org.apache.camel.component.box.api.BoxFoldersManager;
import org.apache.camel.component.box.api.BoxGroupsManager;
import org.apache.camel.component.box.api.BoxSearchManager;
import org.apache.camel.component.box.api.BoxTasksManager;
import org.apache.camel.component.box.api.BoxUsersManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.component.box.internal.BoxConnectionHelper;
import org.apache.camel.component.box.internal.BoxConstants;
import org.apache.camel.component.box.internal.BoxPropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * For uploading downloading and managing files folders groups collaborations etc on box DOT com.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "box", title = "Box", syntax = "box:apiName/methodName",
    consumerClass = BoxConsumer.class, consumerPrefix = "consumer", label = "api,file,cloud", lenientProperties = true)
public class BoxEndpoint extends AbstractApiEndpoint<BoxApiName, BoxConfiguration> {

    @UriParam
    private BoxConfiguration configuration;

    // cached connection
    private BoxAPIConnection boxConnection;

    private Object apiProxy;

    private boolean boxConnectionShared;

    public BoxEndpoint(String uri, BoxComponent component, BoxApiName apiName, String methodName,
                       BoxConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, BoxApiCollection.getCollection().getHelper(apiName),
                endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    @Override
    public BoxComponent getComponent() {
        return (BoxComponent) super.getComponent();
    }

    /**
     * The BoxAPIConnection of endpoint.
     */
    public BoxAPIConnection getBoxConnection() {
        return boxConnection;
    }

    public Producer createProducer() throws Exception {
        return new BoxProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final BoxConsumer consumer = new BoxConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<BoxConfiguration> getPropertiesHelper() {
        return BoxPropertiesHelper.getHelper();
    }

    protected String getThreadProfileName() {
        return BoxConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        // create connection eagerly, a good way to validate configuration
        createBoxConnection();

    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        if (apiProxy == null) {
            // create API proxy lazily
            createApiProxy(args);
        }
        return apiProxy;
    }

    private void createBoxConnection() {
        final BoxComponent component = getComponent();
        this.boxConnectionShared = configuration.equals(getComponent().getConfiguration());
        if (boxConnectionShared) {
            // get shared singleton connection from Component
            this.boxConnection = component.getBoxConnection();
        } else {
            this.boxConnection = BoxConnectionHelper.createConnection(configuration);
        }
    }

    private void createApiProxy(Map<String, Object> args) {
        switch (apiName) {
        case COLLABORATIONS:
            apiProxy = new BoxCollaborationsManager(getBoxConnection());
            break;
        case COMMENTS:
            apiProxy = new BoxCommentsManager(getBoxConnection());
            break;
        case EVENT_LOGS:
            apiProxy = new BoxEventLogsManager(getBoxConnection());
            break;
        case EVENTS:
            apiProxy = new BoxEventsManager(getBoxConnection());
            break;
        case FILES:
            apiProxy = new BoxFilesManager(getBoxConnection());
            break;
        case FOLDERS:
            apiProxy = new BoxFoldersManager(getBoxConnection());
            break;
        case GROUPS:
            apiProxy = new BoxGroupsManager(getBoxConnection());
            break;
        case SEARCH:
            apiProxy = new BoxSearchManager(getBoxConnection());
            break;
        case TASKS:
            apiProxy = new BoxTasksManager(getBoxConnection());
            break;
        case USERS:
            apiProxy = new BoxUsersManager(getBoxConnection());
            break;
        default:
            throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }
}
