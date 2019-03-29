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
package org.apache.camel.component.wordpress.api;

import java.util.HashMap;

import org.apache.camel.component.wordpress.api.service.WordpressService;
import org.apache.camel.component.wordpress.api.service.WordpressServicePosts;
import org.apache.camel.component.wordpress.api.service.WordpressServiceUsers;
import org.apache.camel.component.wordpress.api.service.impl.WordpressServicePostsAdapter;
import org.apache.camel.component.wordpress.api.service.impl.WordpressServiceUsersAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;

public final class WordpressServiceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressServiceProvider.class);

    private HashMap<Class<? extends WordpressService>, WordpressService> services;
    private WordpressAPIConfiguration configuration;

    private WordpressServiceProvider() {

    }

    private static class ServiceProviderHolder {
        private static final WordpressServiceProvider INSTANCE = new WordpressServiceProvider();
    }

    public static WordpressServiceProvider getInstance() {
        return ServiceProviderHolder.INSTANCE;
    }

    public void init(String wordpressApiUrl) {
        this.init(wordpressApiUrl, WordpressConstants.API_VERSION);
    }

    public void init(String wordpressApiUrl, String apiVersion) {
        this.init(new WordpressAPIConfiguration(wordpressApiUrl, apiVersion));
    }

    public void init(WordpressAPIConfiguration config) {
        checkNotNull(emptyToNull(config.getApiUrl()), "Please inform the Wordpress API url , eg.: http://myblog.com/wp-json/wp");

        if (isNullOrEmpty(config.getApiVersion())) {
            config.setApiVersion(WordpressConstants.API_VERSION);
        }

        final WordpressServicePosts servicePosts = new WordpressServicePostsAdapter(config.getApiUrl(), config.getApiVersion());
        final WordpressServiceUsers serviceUsers = new WordpressServiceUsersAdapter(config.getApiUrl(), config.getApiVersion());

        servicePosts.setWordpressAuthentication(config.getAuthentication());
        serviceUsers.setWordpressAuthentication(config.getAuthentication());

        this.services = new HashMap<>();
        this.services.put(WordpressServicePosts.class, servicePosts);
        this.services.put(WordpressServiceUsers.class, serviceUsers);
        this.configuration = config;

        LOGGER.info("Wordpress Service Provider initialized using base URL: {}, API Version {}", config.getApiUrl(), config.getApiVersion());
    }

    @SuppressWarnings("unchecked")
    public <T extends WordpressService> T getService(Class<T> wordpressServiceClazz) {
        T service = (T)this.services.get(wordpressServiceClazz);
        if (service == null) {
            throw new IllegalArgumentException(String.format("Couldn't find a Wordpress Service '%s'", wordpressServiceClazz));
        }
        return service;
    }

    public boolean hasAuthentication() {
        if (this.configuration != null) {
            return this.configuration.getAuthentication() != null;
        }
        return false;
    }

}
