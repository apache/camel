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
package org.apache.camel.component.linkedin;

import java.util.Arrays;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.linkedin.api.CommentsResource;
import org.apache.camel.component.linkedin.api.CompaniesResource;
import org.apache.camel.component.linkedin.api.EnumQueryParamConverterProvider;
import org.apache.camel.component.linkedin.api.GroupsResource;
import org.apache.camel.component.linkedin.api.JobsResource;
import org.apache.camel.component.linkedin.api.LinkedInOAuthRequestFilter;
import org.apache.camel.component.linkedin.api.PeopleResource;
import org.apache.camel.component.linkedin.api.PostsResource;
import org.apache.camel.component.linkedin.api.SearchResource;
import org.apache.camel.component.linkedin.internal.LinkedInApiCollection;
import org.apache.camel.component.linkedin.internal.LinkedInApiName;
import org.apache.camel.component.linkedin.internal.LinkedInConstants;
import org.apache.camel.component.linkedin.internal.LinkedInPropertiesHelper;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.component.AbstractApiEndpoint;
import org.apache.camel.util.component.ApiMethod;
import org.apache.camel.util.component.ApiMethodPropertiesHelper;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;

/**
 * The linkedin component is uses for retrieving LinkedIn user profiles, connections, companies, groups, posts, etc.
 */
@UriEndpoint(firstVersion = "2.14.0", scheme = "linkedin", title = "Linkedin", syntax = "linkedin:apiName/methodName", label = "api,cloud,social",
        consumerClass = LinkedInConsumer.class, lenientProperties = true)
public class LinkedInEndpoint extends AbstractApiEndpoint<LinkedInApiName, LinkedInConfiguration> {

    protected static final String FIELDS_OPTION = "fields";
    private static final String DEFAULT_FIELDS_SELECTOR = "";

    @UriParam
    private final LinkedInConfiguration configuration;

    // OAuth request filter
    private LinkedInOAuthRequestFilter requestFilter;

    // Resource API proxy
    private Object resourceProxy;

    public LinkedInEndpoint(String uri, LinkedInComponent component,
                         LinkedInApiName apiName, String methodName, LinkedInConfiguration endpointConfiguration) {
        super(uri, component, apiName, methodName, LinkedInApiCollection.getCollection().getHelper(apiName), endpointConfiguration);
        this.configuration = endpointConfiguration;
    }

    public Producer createProducer() throws Exception {
        return new LinkedInProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        // make sure inBody is not set for consumers
        if (inBody != null) {
            throw new IllegalArgumentException("Option inBody is not supported for consumer endpoint");
        }
        final LinkedInConsumer consumer = new LinkedInConsumer(this, processor);
        // also set consumer.* properties
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected ApiMethodPropertiesHelper<LinkedInConfiguration> getPropertiesHelper() {
        return LinkedInPropertiesHelper.getHelper();
    }

    protected String getThreadProfileName() {
        return LinkedInConstants.THREAD_PROFILE_NAME;
    }

    @Override
    protected void afterConfigureProperties() {
        createProxy();
    }

    // create API proxy, set connection properties, etc.
    private void createProxy() {
        // create endpoint filter or get shared filter if configuration values are same as component
        requestFilter = getComponent().getRequestFilter(configuration);

        final Class<?> proxyClass;
        switch (apiName) {
        case COMMENTS:
            proxyClass = CommentsResource.class;
            break;
        case COMPANIES:
            proxyClass = CompaniesResource.class;
            break;
        case GROUPS:
            proxyClass = GroupsResource.class;
            break;
        case JOBS:
            proxyClass = JobsResource.class;
            break;
        case PEOPLE:
            proxyClass = PeopleResource.class;
            break;
        case POSTS:
            proxyClass = PostsResource.class;
            break;
        case SEARCH:
            proxyClass = SearchResource.class;
            break;
        default:
            throw new IllegalArgumentException("Invalid API name " + apiName);
        }

        // create endpoint proxy
        resourceProxy = JAXRSClientFactory.create(LinkedInOAuthRequestFilter.BASE_ADDRESS, proxyClass,
            Arrays.asList(new Object[]{requestFilter, new EnumQueryParamConverterProvider()}));
    }

    @Override
    public Object getApiProxy(ApiMethod method, Map<String, Object> args) {
        return resourceProxy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (resourceProxy == null) {
            createProxy();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (resourceProxy != null) {
            try {
                WebClient.client(resourceProxy).close();
            } catch (Exception e) {
                log.warn("Error closing LinkedIn REST proxy: " + e.getMessage(), e);
            }
            resourceProxy = null;
        }

        if (requestFilter != null) {
            getComponent().closeRequestFilter(requestFilter);
            requestFilter = null;
        }
    }

    @Override
    public LinkedInComponent getComponent() {
        return (LinkedInComponent) super.getComponent();
    }

    @Override
    public void interceptProperties(Map<String, Object> properties) {
        if (!properties.containsKey(FIELDS_OPTION)) {
            properties.put(FIELDS_OPTION, DEFAULT_FIELDS_SELECTOR);
        }
    }
}
