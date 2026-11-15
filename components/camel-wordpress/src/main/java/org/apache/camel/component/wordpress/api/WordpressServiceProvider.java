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

import java.util.Map;

import org.apache.camel.component.wordpress.api.service.WordpressService;
import org.apache.camel.component.wordpress.api.service.WordpressServicePosts;
import org.apache.camel.component.wordpress.api.service.WordpressServiceUsers;
import org.apache.camel.component.wordpress.api.service.impl.WordpressServicePostsAdapter;
import org.apache.camel.component.wordpress.api.service.impl.WordpressServiceUsersAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives access to the WordPress services (posts and users) of one WordPress site.
 * <p>
 * Use {@link #create(WordpressAPIConfiguration)} to get a provider of its own for a site. Each WordPress endpoint holds
 * its own provider.
 */
public final class WordpressServiceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressServiceProvider.class);

    private volatile Map<Class<? extends WordpressService>, WordpressService> services;
    private volatile WordpressAPIConfiguration configuration;

    private WordpressServiceProvider() {

    }

    private static class ServiceProviderHolder {
        private static final WordpressServiceProvider INSTANCE = new WordpressServiceProvider();
    }

    /**
     * Creates a new provider for the WordPress site of the given configuration.
     *
     * @param  config the site URL, API version and authentication
     * @return        a provider that is not shared with anyone else
     */
    public static WordpressServiceProvider create(WordpressAPIConfiguration config) {
        final WordpressServiceProvider provider = new WordpressServiceProvider();
        provider.doInit(config);
        return provider;
    }

    /**
     * The provider shared by the whole JVM.
     *
     * @deprecated the WordPress endpoints do not use this instance; use {@link #create(WordpressAPIConfiguration)}
     */
    @Deprecated(since = "4.24")
    public static WordpressServiceProvider getInstance() {
        return ServiceProviderHolder.INSTANCE;
    }

    /**
     * @deprecated use {@link #create(WordpressAPIConfiguration)}
     */
    @Deprecated(since = "4.24")
    public void init(String wordpressApiUrl) {
        this.doInit(new WordpressAPIConfiguration(wordpressApiUrl, WordpressConstants.API_VERSION));
    }

    /**
     * @deprecated use {@link #create(WordpressAPIConfiguration)}
     */
    @Deprecated(since = "4.24")
    public void init(String wordpressApiUrl, String apiVersion) {
        this.doInit(new WordpressAPIConfiguration(wordpressApiUrl, apiVersion));
    }

    /**
     * @deprecated use {@link #create(WordpressAPIConfiguration)}
     */
    @Deprecated(since = "4.24")
    public void init(WordpressAPIConfiguration config) {
        this.doInit(config);
    }

    private void doInit(WordpressAPIConfiguration config) {
        ObjectHelper.notNullOrEmpty(config.getApiUrl(),
                "Please inform the Wordpress API url , eg.: http://myblog.com/wp-json/wp");

        if (config.getApiVersion() == null || config.getApiVersion().isEmpty()) {
            config.setApiVersion(WordpressConstants.API_VERSION);
        }

        final WordpressServicePosts servicePosts = new WordpressServicePostsAdapter(config.getApiUrl(), config.getApiVersion());
        final WordpressServiceUsers serviceUsers = new WordpressServiceUsersAdapter(config.getApiUrl(), config.getApiVersion());

        servicePosts.setWordpressAuthentication(config.getAuthentication());
        serviceUsers.setWordpressAuthentication(config.getAuthentication());

        this.services = Map.of(WordpressServicePosts.class, servicePosts, WordpressServiceUsers.class, serviceUsers);
        this.configuration = config;

        LOGGER.info("Wordpress Service Provider initialized using base URL: {}, API Version {}", config.getApiUrl(),
                config.getApiVersion());
    }

    @SuppressWarnings("unchecked")
    public <T extends WordpressService> T getService(Class<T> wordpressServiceClazz) {
        final Map<Class<? extends WordpressService>, WordpressService> current = this.services;
        T service = current != null ? (T) current.get(wordpressServiceClazz) : null;
        if (service == null) {
            throw new IllegalArgumentException(String.format("Couldn't find a Wordpress Service '%s'", wordpressServiceClazz));
        }
        return service;
    }

    public boolean hasAuthentication() {
        final WordpressAPIConfiguration current = this.configuration;
        return current != null && current.getAuthentication() != null;
    }

}
