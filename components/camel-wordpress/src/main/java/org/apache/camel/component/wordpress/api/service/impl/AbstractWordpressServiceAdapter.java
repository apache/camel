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
package org.apache.camel.component.wordpress.api.service.impl;

import java.util.Collections;

import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import org.apache.camel.component.wordpress.api.auth.WordpressAuthentication;
import org.apache.camel.component.wordpress.api.service.WordpressService;
import org.apache.camel.util.ObjectHelper;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractWordpressServiceAdapter<A> implements WordpressService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWordpressServiceAdapter.class);

    private A spi;

    private final String apiVersion;
    private WordpressAuthentication authentication;

    AbstractWordpressServiceAdapter(final String wordpressUrl, final String apiVersion) {
        this.apiVersion = ObjectHelper.notNullOrEmpty(apiVersion, "apiVersion");

        // @formatter:off
        this.spi = JAXRSClientFactory.create(wordpressUrl, this.getSpiType(),
                Collections.singletonList(new JacksonJsonProvider()));
        // @formatter:on
        WebClient.client(spi).type(MediaType.APPLICATION_JSON_TYPE);
        WebClient.client(spi).accept(MediaType.APPLICATION_JSON_TYPE);

        // TODO: leave this kind of configuration to API clients
        WebClient.getConfig(spi).getHttpConduit().getClient().setAutoRedirect(true);

        /*
         * TODO: aggregate a configuration object to customize the JAXRS behavior, eg.: adding handlers or interceptors
         */
        WebClient.getConfig(spi).getInInterceptors().add(new LoggingInInterceptor());
        WebClient.getConfig(spi).getOutInterceptors().add(new LoggingOutInterceptor());

        LOGGER.info("******* {} API initialized *********", spi.getClass().getSimpleName());
    }

    protected abstract Class<A> getSpiType();

    protected final A getSpi() {
        return spi;
    }

    protected final String getApiVersion() {
        return this.apiVersion;
    }

    @Override
    public final void setWordpressAuthentication(WordpressAuthentication authentication) {
        this.authentication = authentication;
        if (this.authentication != null) {
            this.authentication.configureAuthentication(spi);
        }
    }
}
