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
package org.apache.camel.component.bonita.api;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import org.apache.camel.component.bonita.api.filter.BonitaAuthFilter;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;
import org.apache.camel.component.bonita.api.util.BonitaAPIUtil;

public class BonitaAPIBuilder {

    protected BonitaAPIBuilder() {

    }

    /**
     * Builds an API that sends its requests with a new JAX-RS client. Nothing closes that client; a caller that uses
     * the API for a while should use {@link #build(BonitaAPIConfig, Client)} with a client that it closes.
     */
    public static BonitaAPI build(BonitaAPIConfig bonitaAPIConfig) {
        if (bonitaAPIConfig == null) {
            throw new IllegalArgumentException("bonitaApiConfig is null");
        }
        return build(bonitaAPIConfig, ClientBuilder.newBuilder().build());
    }

    /**
     * Builds an API that sends its requests with the given JAX-RS client. The JSON provider and the login filter of the
     * configuration are registered on the client, and the file inputs of a case are uploaded with it too. The client
     * stays with the caller, which closes it when the API is no longer used.
     */
    public static BonitaAPI build(BonitaAPIConfig bonitaAPIConfig, Client client) {
        if (bonitaAPIConfig == null) {
            throw new IllegalArgumentException("bonitaApiConfig is null");
        }
        client.register(JacksonJsonProvider.class);
        client.register(new BonitaAuthFilter(bonitaAPIConfig));
        WebTarget webTarget = client.target(bonitaAPIConfig.getBaseBonitaURI()).path("/API/bpm");
        // the file inputs are uploaded with the client of this API, so they go to its server with its account
        return new BonitaAPI(bonitaAPIConfig, webTarget, BonitaAPIUtil.forClient(client, bonitaAPIConfig));
    }

}
