/*
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
import org.apache.camel.Category;
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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;

/**
 * Upload, download and manage files, folders, groups, collaborations, etc. on box.com.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "box", title = "Box", syntax = "box:apiName/methodName",
             apiSyntax = "apiName/methodName",
             consumerPrefix = "consumer", category = { Category.CLOUD, Category.FILE, Category.API }, lenientProperties = true)
@Metadata(excludeProperties = "startScheduler,initialDelay,delay,timeUnit,useFixedDelay,pollStrategy,runLoggingLevel,sendEmptyMessageWhenIdle"
                              + ",greedy,scheduler,schedulerProperties,scheduledExecutorService,backoffMultiplier,backoffIdleThreshold,backoffErrorThreshold,repeatCount,bridgeErrorHandler")
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

    @Override
    public Producer createProducer() throws Exception {
        return new BoxProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final BoxConsumer consumer = new BoxConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<BoxConfiguration> getPropertiesHelper() {
        return BoxPropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return BoxConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        if (apiProxy == null) {
            // create API proxy lazily
            createApiProxy(args);
        }
        return apiProxy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        createBoxConnection();
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
