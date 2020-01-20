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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.camel.Processor;
import org.apache.camel.component.undertow.UndertowConsumer;
import org.apache.camel.component.undertow.UndertowEndpoint;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.authz.Roles;



/**
 * Consumer contains decides if request contains required roles (which are defined for endpoint)
 *
 */
public class ElytronConsumer extends UndertowConsumer {

    public ElytronConsumer(UndertowEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public ElytronEndpoint getElytronEndpoint() {
        return (ElytronEndpoint) super.getEndpoint();
    }

    @Override
    public void handleRequest(HttpServerExchange httpExchange) throws Exception {
        SecurityIdentity identity = getElytronEndpoint().getElytronComponent().getSecurityDomain().getCurrentSecurityIdentity();

        if (identity != null) {
            //already authenticated
            Set<String> roles = new HashSet<>();
            Roles identityRoles = identity.getRoles();

            if (identityRoles != null) {
                for (String roleName : identityRoles) {
                    roles.add(roleName);
                }
            }

            if (isAllowed(roles, getElytronEndpoint().getAllowedRolesList())) {
                super.handleRequest(httpExchange);
            } else {
                httpExchange.setStatusCode(StatusCodes.FORBIDDEN);
                httpExchange.endExchange();
            }

            return;
        }

        super.handleRequest(httpExchange);
    }

    public boolean isAllowed(Set<String> roles, List<String> allowedRoles) {
        for (String role : allowedRoles) {
            if (roles.contains(role)) {
                return true;
            }
        }

        return false;
    }
}
