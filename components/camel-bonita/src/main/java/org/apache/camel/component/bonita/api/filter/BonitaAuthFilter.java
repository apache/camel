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
package org.apache.camel.component.bonita.api.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import org.apache.camel.component.bonita.api.util.BonitaAPIConfig;
import org.apache.camel.util.ObjectHelper;

public class BonitaAuthFilter implements ClientRequestFilter {

    private BonitaAPIConfig bonitaApiConfig;

    public BonitaAuthFilter(BonitaAPIConfig bonitaApiConfig) {
        this.bonitaApiConfig = bonitaApiConfig;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (requestContext.getCookies().get("JSESSIONID") == null) {
            String username = bonitaApiConfig.getUsername();
            String password = bonitaApiConfig.getPassword();
            String bonitaApiToken = null;
            if (ObjectHelper.isEmpty(username)) {
                throw new IllegalArgumentException("Username provided is null or empty.");
            }
            if (ObjectHelper.isEmpty(password)) {
                throw new IllegalArgumentException("Password provided is null or empty.");
            }
            ClientBuilder clientBuilder = ClientBuilder.newBuilder();
            Client client = clientBuilder.build();
            WebTarget webTarget =
                    client.target(bonitaApiConfig.getBaseBonitaURI()).path("loginservice");
            MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
            form.add("username", username);
            form.add("password", password);
            form.add("redirect", "false");
            Response response = webTarget.request().accept(MediaType.APPLICATION_FORM_URLENCODED)
                    .post(Entity.form(form));
            Map<String, NewCookie> cr = response.getCookies();
            ArrayList<Object> cookies = new ArrayList<>();
            for (NewCookie cookie : cr.values()) {
                if ("X-Bonita-API-Token".equals(cookie.getName())) {
                    bonitaApiToken = cookie.getValue();
                    requestContext.getHeaders().add("X-Bonita-API-Token", bonitaApiToken);
                }
                cookies.add(cookie.toString());
            }
            requestContext.getHeaders().put("Cookie", cookies);

        }
    }

    public BonitaAPIConfig getBonitaApiConfig() {
        return bonitaApiConfig;
    }

    public void setBonitaApiConfig(BonitaAPIConfig bonitaApiConfig) {
        this.bonitaApiConfig = bonitaApiConfig;
    }
}
