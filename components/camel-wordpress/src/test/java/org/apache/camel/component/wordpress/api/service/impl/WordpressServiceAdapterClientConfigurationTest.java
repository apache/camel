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
import java.util.List;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import org.apache.camel.component.wordpress.api.WordpressConstants;
import org.apache.camel.component.wordpress.api.auth.WordpressBasicAuthentication;
import org.apache.camel.component.wordpress.api.model.User;
import org.apache.camel.component.wordpress.api.service.spi.UsersSPI;
import org.apache.camel.component.wordpress.api.test.WordpressMockServerTestSupport;
import org.apache.camel.component.wordpress.api.test.WordpressServerHttpRequestHandler;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The client created by the service adapters keeps the interceptors CXF sets up for a JAX-RS client: the adapters do
 * not add interceptors of their own.
 */
public class WordpressServiceAdapterClientConfigurationTest extends WordpressMockServerTestSupport {

    @Test
    public void testAdapterKeepsDefaultInterceptors() {
        final WordpressServiceUsersAdapter adapter
                = new WordpressServiceUsersAdapter(getServerBaseUrl(), WordpressConstants.API_VERSION);
        adapter.setWordpressAuthentication(new WordpressBasicAuthentication(
                WordpressServerHttpRequestHandler.USERNAME, WordpressServerHttpRequestHandler.PASSWORD));
        final UsersSPI plain = JAXRSClientFactory.create(getServerBaseUrl(), UsersSPI.class,
                Collections.singletonList(new JacksonJsonProvider()));

        final ClientConfiguration actual = WebClient.getConfig(adapter.getSpi());
        final ClientConfiguration expected = WebClient.getConfig(plain);
        assertThat(types(actual.getOutInterceptors()), is(types(expected.getOutInterceptors())));
        assertThat(types(actual.getInInterceptors()), is(types(expected.getInInterceptors())));
        assertThat(types(actual.getOutFaultInterceptors()), is(types(expected.getOutFaultInterceptors())));
        assertThat(types(actual.getInFaultInterceptors()), is(types(expected.getInFaultInterceptors())));

        final User user = adapter.retrieve(1);
        assertThat(user, not(nullValue()));
    }

    private static List<String> types(List<Interceptor<? extends Message>> interceptors) {
        return interceptors.stream().map(i -> i.getClass().getName()).sorted().toList();
    }
}
