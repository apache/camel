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
package org.apache.camel.component.google.common;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;

/**
 * Sets the options and headers of an exchange on the request that a method of a Google API client builds, for the
 * components that are generated from a Google API client (Calendar, Drive, Mail).
 */
public final class GoogleApiRequestHelper {

    /**
     * The options that make up the credential of the client. The client sends the credential in the Authorization
     * header, so they are not set on the request, where accessToken would become the access_token query parameter.
     * p12FileName is the key file of a service account (Calendar).
     */
    public static final Set<String> CREDENTIAL_OPTIONS
            = Set.of("clientId", "clientSecret", "accessToken", "refreshToken", "serviceAccountKey", "delegate",
                    "p12FileName");

    private GoogleApiRequestHelper() {
    }

    /**
     * Sets the given properties on the request, such as the optional parameters of the method and the standard
     * parameters of the Google API, except the options of the credential (see {@link #CREDENTIAL_OPTIONS}).
     *
     * @param camelContext the Camel context
     * @param request      the request built by the method of the API client
     * @param properties   the options of the endpoint and the headers of the exchange
     */
    public static void configureRequest(CamelContext camelContext, Object request, Map<String, Object> properties)
            throws Exception {
        BeanIntrospection beanIntrospection = PluginHelper.getBeanIntrospection(camelContext);
        for (Map.Entry<String, Object> p : properties.entrySet()) {
            if (!CREDENTIAL_OPTIONS.contains(p.getKey())) {
                beanIntrospection.setProperty(camelContext, request, p.getKey(), p.getValue());
            }
        }
    }
}
