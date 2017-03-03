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

import java.util.Map;

import com.box.sdk.BoxAPIConnection;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.box2.api.Box2CollaborationsManager;
import org.apache.camel.component.box2.api.Box2CommentsManager;
import org.apache.camel.component.box2.api.Box2EventLogsManager;
import org.apache.camel.component.box2.api.Box2EventsManager;
import org.apache.camel.component.box2.api.Box2FilesManager;
import org.apache.camel.component.box2.api.Box2FoldersManager;
import org.apache.camel.component.box2.api.Box2GroupsManager;
import org.apache.camel.component.box2.api.Box2SearchManager;
import org.apache.camel.component.box2.api.Box2TasksManager;
import org.apache.camel.component.box2.api.Box2UsersManager;
import org.apache.camel.component.box2.internal.Box2ApiCollection;
import org.apache.camel.component.box2.internal.Box2ApiName;
import org.apache.camel.component.box2.internal.Box2ConnectionHelper;
import org.apache.camel.component.box2.internal.Box2Constants;
import org.apache.camel.component.box2.internal.Box2PropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * Represents a Box2 endpoint.
 * 
 * @author <a href="mailto:punkhornsw@gmail.com">William Collins</a>
 * 
 */
@UriEndpoint(scheme = "box2", title = "Box2", syntax = "box2:apiName/methodName", consumerClass = Box2Consumer.class, consumerPrefix = "consumer", label = "api,file,cloud", lenientProperties = true)
public class Box2Endpoint extends AbstractApiEndpoint<Box2ApiName, Box2Configuration> {

    @UriParam(name = "configuration", description = "Box 2 Configuration")
    private Box2Configuration configuration;

    // cached connection
    private BoxAPIConnection boxConnection;

    private Object apiProxy;

    private boolean boxConnectionShared;

    public Box2Endpoint(String uri, Box2Component component, Box2ApiName apiName, String methodName,
            Box2Configuration endpointConfiguration) {
        super(uri, component, apiName, methodName, Box2ApiCollection.getCollection().getHelper(apiName),
                endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.camel.impl.DefaultEndpoint#getComponent()
     */
    @Override
    public Box2Component getComponent() {
        return (Box2Component) super.getComponent();
    }

    /**
     * The BoxAPIConnection of endpoint.
     * 
     * @return the BoxAPIConnection of endpoint.
     */
    public BoxAPIConnection getBoxConnection() {
        return boxConnection;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.camel.Endpoint#createProducer()
     */
    public Producer createProducer() throws Exception {
        return new Box2Producer(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.camel.Endpoint#createConsumer(org.apache.camel.Processor)
     */
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final Box2Consumer consumer = new Box2Consumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.camel.util.component.AbstractApiEndpoint#getPropertiesHelper()
     */
    @Override
    protected ApiMethodPropertiesHelper<Box2Configuration> getPropertiesHelper() {
        return Box2PropertiesHelper.getHelper();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.camel.util.component.AbstractApiEndpoint#getThreadProfileName(
     * )
     */
    protected String getThreadProfileName() {
        return Box2Constants.THREAD_PROFILE_NAME;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.camel.util.component.AbstractApiEndpoint#
     * afterConfigureProperties()
     */
    @Override
    protected void afterConfigureProperties() {
        // create connection eagerly, a good way to validate configuration
        createBoxConnection();

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.camel.util.component.AbstractApiEndpoint#getApiProxy(org.
     * apache.camel.util.component.ApiMethod, java.util.Map)
     */
    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        if (apiProxy == null) {
            // create API proxy lazily
            createApiProxy(args);
        }
        return apiProxy;
    }

    private void createBoxConnection() {
        final Box2Component component = getComponent();
        this.boxConnectionShared = configuration.equals(getComponent().getConfiguration());
        if (boxConnectionShared) {
            // get shared singleton connection from Component
            this.boxConnection = component.getBoxConnection();
        } else {
            this.boxConnection = Box2ConnectionHelper.createConnection(configuration);
        }
    }

    private void createApiProxy(Map<String, Object> args) {
        switch (apiName) {
        case COLLABORATIONS:
            apiProxy = new Box2CollaborationsManager(getBoxConnection());
            break;
        case COMMENTS:
            apiProxy = new Box2CommentsManager(getBoxConnection());
            break;
        case EVENT_LOGS:
            apiProxy = new Box2EventLogsManager(getBoxConnection());
            break;
        case EVENTS:
            apiProxy = new Box2EventsManager(getBoxConnection());
            break;
        case FILES:
            apiProxy = new Box2FilesManager(getBoxConnection());
            break;
        case FOLDERS:
            apiProxy = new Box2FoldersManager(getBoxConnection());
            break;
        case GROUPS:
            apiProxy = new Box2GroupsManager(getBoxConnection());
            break;
        case SEARCH:
            apiProxy = new Box2SearchManager(getBoxConnection());
            break;
        case TASKS:
            apiProxy = new Box2TasksManager(getBoxConnection());
            break;
        case USERS:
            apiProxy = new Box2UsersManager(getBoxConnection());
            break;
        default:
            throw new IllegalArgumentException("Invalid API name " + apiName);
        }
    }
}
