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
package org.apache.camel.component.wordpress;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.wordpress.api.WordpressAPIConfiguration;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.auth.WordpressBasicAuthentication;
import org.apache.camel.component.wordpress.api.model.SearchCriteria;
import org.apache.camel.component.wordpress.consumer.WordpressPostConsumer;
import org.apache.camel.component.wordpress.consumer.WordpressUserConsumer;
import org.apache.camel.component.wordpress.producer.WordpressPostProducer;
import org.apache.camel.component.wordpress.producer.WordpressUserProducer;
import org.apache.camel.component.wordpress.proxy.WordpressOperationType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;

/**
 * Manage posts and users using the WordPress API.
 */
@UriEndpoint(firstVersion = "2.21.0", scheme = "wordpress", title = "WordPress", syntax = "wordpress:operation",
             category = { Category.CLOUD, Category.API, Category.CMS })
public class WordpressEndpoint extends DefaultEndpoint {

    public static final String ENDPOINT_SERVICE_POST = "post, user";

    @UriPath(description = "The endpoint operation.", enums = ENDPOINT_SERVICE_POST)
    @Metadata(required = true)
    private String operation;

    @UriPath(description = "The second part of an endpoint operation. Needed only when endpoint semantic is not enough, like wordpress:post:delete",
             enums = "delete")
    private String operationDetail;

    @UriParam
    private WordpressConfiguration configuration;

    public WordpressEndpoint(String uri, WordpressComponent component, WordpressConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public WordpressConfiguration getConfiguration() {
        return configuration;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperationDetail() {
        return operationDetail;
    }

    public void setOperationDetail(String operationDetail) {
        this.operationDetail = operationDetail;
    }

    @Override
    public Producer createProducer() throws Exception {
        switch (WordpressOperationType.valueOf(operation)) {
            case post:
                return new WordpressPostProducer(this);
            case user:
                return new WordpressUserProducer(this);
            default:
                break;
        }
        throw new UnsupportedOperationException(String.format("Operation '%s' not supported.", operation));
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        switch (WordpressOperationType.valueOf(operation)) {
            case post:
                return new WordpressPostConsumer(this, processor);
            case user:
                return new WordpressUserConsumer(this, processor);
            default:
                break;
        }
        throw new UnsupportedOperationException(String.format("Operation '%s' not supported.", operation));
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);

        // set configuration properties first
        try {
            if (configuration == null) {
                configuration = new WordpressConfiguration();
            }
            PropertyBindingSupport.bindProperties(getCamelContext(), configuration, options);

            if (configuration.getSearchCriteria() == null) {
                final SearchCriteria searchCriteria = WordpressOperationType.valueOf(operation).getCriteriaType().newInstance();
                Map<String, Object> criteriaOptions = PropertiesHelper.extractProperties(options, "criteria.");
                // any property that has a "," should be a List
                criteriaOptions = criteriaOptions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    if (e.toString().contains(",")) {
                        return Arrays.asList(e.toString().split(","));
                    }
                    return e.getValue();
                }));
                PropertyBindingSupport.bindProperties(getCamelContext(), searchCriteria, criteriaOptions);
                configuration.setSearchCriteria(searchCriteria);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        // validate configuration
        configuration.validate();
        this.initServiceProvider();
    }

    private void initServiceProvider() {
        final WordpressAPIConfiguration apiConfiguration
                = new WordpressAPIConfiguration(configuration.getUrl(), configuration.getApiVersion());
        // basic auth
        if (ObjectHelper.isNotEmpty(configuration.getUser())) {
            apiConfiguration
                    .setAuthentication(new WordpressBasicAuthentication(configuration.getUser(), configuration.getPassword()));
        }

        WordpressServiceProvider.getInstance().init(apiConfiguration);
    }

}
