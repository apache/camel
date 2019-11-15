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

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.camel.component.bonita.api.filter.BonitaAuthFilter;
import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;

public class BonitaAPIBuilder {

    protected BonitaAPIBuilder() {

    }

    public static BonitaAPI build(BonitaAPIConfig bonitaAPIConfig) {
        if (bonitaAPIConfig == null) {
            throw new IllegalArgumentException("bonitaApiConfig is null");
        }
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.register(JacksonJsonProvider.class);
        Client client = clientBuilder.build();
        client.register(new BonitaAuthFilter(bonitaAPIConfig));
        WebTarget webTarget = client.target(bonitaAPIConfig.getBaseBonitaURI()).path("/API/bpm");
        return new BonitaAPI(bonitaAPIConfig, webTarget);
    }

}
