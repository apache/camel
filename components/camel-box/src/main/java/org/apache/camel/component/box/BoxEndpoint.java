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

import com.box.boxjavalibv2.BoxClient;
import com.box.boxjavalibv2.resourcemanagers.IBoxResourceManager;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.component.box.internal.BoxClientHelper;
import org.apache.camel.component.box.internal.BoxConstants;
import org.apache.camel.component.box.internal.BoxPropertiesHelper;
import org.apache.camel.component.box.internal.CachedBoxClient;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;

/**
 * Represents a Box endpoint.
 */
@UriEndpoint(scheme = "box", title = "Box", syntax = "box:apiName/methodName", consumerClass = BoxConsumer.class, consumerPrefix = "consumer", label = "api,file,cloud",
        lenientProperties = true)
// need to be lenient as the box component has a bunch of generated configuration classes that lacks documentation
public class BoxEndpoint extends AbstractApiEndpoint<BoxApiName, BoxConfiguration> {

    private static final String SHARED_LINK_PROPERTY = "sharedLink";
    private static final String SHARED_PASSWORD_PROPERTY = "sharedPassword";

    @UriParam
    private BoxConfiguration configuration;

    // cached client
    private CachedBoxClient cachedBoxClient;

    // proxy manager
    private IBoxResourceManager apiProxy;

    // configuration values for shared links
    private String sharedLink;
    private String sharedPassword;
    private boolean boxClientShared;

    public BoxEndpoint(String uri, BoxComponent component,
                       BoxApiName apiName, String methodName, BoxConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, BoxApiCollection.getCollection().getHelper(apiName), endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    public Producer createProducer() throws Exception {
        // validate producer APIs
        if (getApiName() == BoxApiName.POLL_EVENTS) {
            throw new IllegalArgumentException("Producer endpoints do not support endpoint prefix "
                + BoxApiName.POLL_EVENTS.getName());
        }
        return new BoxProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }

        // validate consumer APIs
        if (getApiName() != BoxApiName.POLL_EVENTS) {
            throw new IllegalArgumentException("Consumer endpoint only supports endpoint prefix "
                + BoxApiName.POLL_EVENTS.getName());
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
        // create client eagerly, a good way to validate configuration
        createBoxClient();

        this.sharedLink = configuration.getSharedLink();
        this.sharedPassword = configuration.getSharedPassword();

        // validate shared endpoints
        switch (getApiName()) {
        case SHARED_COMMENTS:
        case SHARED_FILES:
        case SHARED_FOLDERS:
        case SHARED_ITEMS:
            if (ObjectHelper.isEmpty(sharedLink)) {
                log.warn("Header properties sharedLink and sharedPassword MUST be provided for endpoint {}",
                    getEndpointUri());
            }
            break;
        default:
        }
    }

    private void createBoxClient() {
        final BoxComponent component = getComponent();
        this.boxClientShared = configuration.equals(getComponent().getConfiguration());
        if (boxClientShared) {
            // get shared singleton client from Component
            cachedBoxClient = component.getBoxClient();
        } else {
            cachedBoxClient = BoxClientHelper.createBoxClient(configuration);
        }
    }

    @Override
    public BoxComponent getComponent() {
        return (BoxComponent) super.getComponent();
    }

    @Override
    public void interceptProperties(Map<String, Object> properties) {
        // set shared link and password from configuration if not set as header properties
        if (!properties.containsKey(SHARED_LINK_PROPERTY) && !ObjectHelper.isEmpty(sharedLink)) {
            properties.put(SHARED_LINK_PROPERTY, sharedLink);
        }
        if (!properties.containsKey(SHARED_PASSWORD_PROPERTY) && !ObjectHelper.isEmpty(sharedPassword)) {
            properties.put(SHARED_PASSWORD_PROPERTY, sharedPassword);
        }
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        if (apiProxy == null) {
            // create API proxy lazily
            createApiProxy(args);
        }
        return apiProxy;
    }

    private void createApiProxy(Map<String, Object> args) {

        // get shared link and password from args
        final String sharedLink = (String) args.get("sharedLink");
        final String sharedPassword = (String) args.get("sharedPassword");

        switch (apiName) {
        case SHARED_COMMENTS:
        case SHARED_FILES:
        case SHARED_FOLDERS:
        case SHARED_ITEMS:
            if (ObjectHelper.isEmpty(sharedLink)) {
                throw new IllegalArgumentException("Missing required property sharedLink");
            }
        default:
        }

        final BoxClient boxClient = cachedBoxClient.getBoxClient();
        switch (apiName) {
        case COLLABORATIONS:
            apiProxy = boxClient.getCollaborationsManager();
            break;
        case COMMENTS:
            apiProxy = boxClient.getCommentsManager();
            break;
        case EVENTS:
            apiProxy = boxClient.getEventsManager();
            break;
        case FILES:
            apiProxy = boxClient.getFilesManager();
            break;
        case FOLDERS:
            apiProxy = boxClient.getFoldersManager();
            break;
        case GROUPS:
            apiProxy = boxClient.getGroupsManager();
            break;
        case SEARCH:
            apiProxy = boxClient.getSearchManager();
            break;
        case SHARED_FILES:
            apiProxy = boxClient.getSharedFilesManager(sharedLink, sharedPassword);
            break;
        case SHARED_FOLDERS:
            apiProxy = boxClient.getSharedFoldersManager(sharedLink, sharedPassword);
            break;
        case SHARED_COMMENTS:
            apiProxy = boxClient.getSharedCommentsManager(sharedLink, sharedPassword);
            break;
        case SHARED_ITEMS:
            apiProxy = boxClient.getSharedItemsManager(sharedLink, sharedPassword);
            break;
        case USERS:
            apiProxy = boxClient.getUsersManager();
            break;
        default:
        }
    }

    @Override
    protected void doStart() throws Exception {
        BoxClientHelper.getOAuthToken(configuration, cachedBoxClient);
    }

    @Override
    protected void doStop() throws Exception {
        try {
            if (!boxClientShared) {
                // while there is no way to suspend BoxClient, we can close idle connections to be nice
                BoxClientHelper.closeIdleConnections(cachedBoxClient);
            }
        } finally {
            super.doStop();
        }
    }

    @Override
    public void doShutdown() throws Exception {
        try {
            // cleanup if BoxClient is not shared
            if (!boxClientShared) {
                BoxClientHelper.shutdownBoxClient(configuration, cachedBoxClient);
            }
        } finally {
            cachedBoxClient = null;
            super.doShutdown();
        }
    }

    public CachedBoxClient getBoxClient() {
        return cachedBoxClient;
    }
}
