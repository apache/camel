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

package org.apache.camel.component.elytron;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.undertow.server.HttpServerExchange;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.undertow.UndertowComponent;
import org.apache.camel.component.undertow.UndertowEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * The elytron component is allows you to work with the Elytron Security Framework
 *
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "elytron", title = "Elytron", syntax = "elytron:httpURI",
        label = "http", lenientProperties = true, extendsScheme = "undertow")
public class ElytronEndpoint extends UndertowEndpoint {

    /**
     * Name of the header which contains associated security identity if request is authenticated.
     */
    public static final String SECURITY_IDENTITY_HEADER = "securityIdentity";

    @UriParam(label = "common")
    private String allowedRoles = "";
    private List<String> allowedRolesList = Collections.emptyList();

    public ElytronEndpoint(String uri, UndertowComponent component) {
        super(uri, component);
    }

    public ElytronComponent getElytronComponent() {
        return (ElytronComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new ElytronConsumer(this, processor);
    }

    @Override
    public Exchange createExchange(HttpServerExchange httpExchange) throws Exception {
        Exchange exchange = super.createExchange(httpExchange);

        SecurityIdentity securityIdentity = getElytronComponent().getSecurityDomain().getCurrentSecurityIdentity();
        //add security principal to headers
        exchange.getIn().setHeader(SECURITY_IDENTITY_HEADER, securityIdentity);

        return exchange;
    }

    public List<String> getAllowedRolesList() {
        return allowedRolesList;
    }

    /**
     * Comma separated list of allowed roles.
     */
    public String getAllowedRoles() {
        return allowedRoles;
    }

    public void setAllowedRoles(String allowedRoles) {
        this.allowedRolesList = allowedRoles == null ? null : Arrays.asList(allowedRoles.split("\\s*,\\s*"));
    }
}
