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
package org.apache.camel.component.zendesk;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.zendesk.internal.ZendeskApiCollection;
import org.apache.camel.component.zendesk.internal.ZendeskApiName;
import org.apache.camel.component.zendesk.internal.ZendeskConstants;
import org.apache.camel.component.zendesk.internal.ZendeskHelper;
import org.apache.camel.component.zendesk.internal.ZendeskPropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.component.AbstractApiEndpoint;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodPropertiesHelper;
import org.apache.camel.util.IOHelper;
import org.zendesk.client.v2.Zendesk;

/**
 * Manage Zendesk tickets, users, organizations, etc.
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "zendesk", title = "Zendesk", syntax = "zendesk:methodName",
             apiSyntax = "methodName",
             consumerPrefix = "consumer", category = { Category.CLOUD, Category.API, Category.SAAS })
public class ZendeskEndpoint extends AbstractApiEndpoint<ZendeskApiName, ZendeskConfiguration> {

    @UriParam
    private ZendeskConfiguration configuration;

    private Zendesk apiProxy;

    public ZendeskEndpoint(String uri, ZendeskComponent component, ZendeskApiName apiName, String methodName,
                           ZendeskConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, ZendeskApiCollection.getCollection().getHelper(apiName),
              endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    @Override
    public ZendeskComponent getComponent() {
        return (ZendeskComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ZendeskProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final ZendeskConsumer consumer = new ZendeskConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // verify configuration is valid
        getZendesk();
    }

    @Override
    public void doStop() throws Exception {
        IOHelper.close(apiProxy);
        super.doStop();
    }

    @Override
    protected ApiMethodPropertiesHelper<ZendeskConfiguration> getPropertiesHelper() {
        return ZendeskPropertiesHelper.getHelper(getCamelContext());
    }

    @Override
    protected String getThreadProfileName() {
        return ZendeskConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return getZendesk();
    }

    private Zendesk getZendesk() {
        if (apiProxy == null) {
            if (getConfiguration().equals(getComponent().getConfiguration())) {
                apiProxy = getComponent().getZendesk();
            } else {
                apiProxy = ZendeskHelper.create(getConfiguration());
            }
        }
        return apiProxy;
    }

}
