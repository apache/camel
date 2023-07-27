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

import java.security.Provider;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.undertow.spi.UndertowSecurityProvider;
import org.wildfly.elytron.web.undertow.server.ElytronContextAssociationHandler;
import org.wildfly.elytron.web.undertow.server.ElytronRunAsHandler;
import org.wildfly.security.WildFlyElytronBaseProvider;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismConfigurationSelector;
import org.wildfly.security.auth.server.MechanismRealmConfiguration;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.http.HttpAuthenticationFactory;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.http.HttpAuthenticationException;
import org.wildfly.security.http.HttpServerAuthenticationMechanismFactory;
import org.wildfly.security.http.util.FilterServerMechanismFactory;
import org.wildfly.security.http.util.SecurityProviderServerMechanismFactory;

/**
 * Implementation of `UndertowSecurityProvider` which adds elytron capability into camel-undertow. Provider requires
 * instance of `ElytronSecurityConfiguration` to be provided as `securityConfiguration` parameter in camel-undertow.
 */
@Deprecated
public class ElytronSecurityProvider implements UndertowSecurityProvider {
    /**
     * Name of the header which contains associated security identity if request is authenticated.
     */
    public static final String SECURITY_IDENTITY_HEADER = "securityIdentity";

    private SecurityDomain securityDomain;
    private WildFlyElytronBaseProvider elytronProvider;
    private String mechanismName;

    /**
     * Provider adds header `securityIdentity` with value of type `SecurityIdentity` after successful authentication.
     */
    @Override
    public void addHeader(BiConsumer<String, Object> consumer, HttpServerExchange httpExchange) throws Exception {
        SecurityIdentity securityIdentity = this.securityDomain.getCurrentSecurityIdentity();
        //add security principal to headers
        consumer.accept(SECURITY_IDENTITY_HEADER, securityIdentity);
    }

    /**
     * Authentication is verified by securityDomain from configuration.
     */
    @Override
    public int authenticate(HttpServerExchange httpExchange, List<String> allowedRoles) throws Exception {
        SecurityIdentity identity = this.securityDomain.getCurrentSecurityIdentity();

        if (identity != null) {
            //already authenticated
            Set<String> roles = new HashSet<>();
            Roles identityRoles = identity.getRoles();

            if (identityRoles != null) {
                for (String roleName : identityRoles) {
                    roles.add(roleName);
                }
            }

            if (isAllowed(roles, allowedRoles)) {
                return StatusCodes.OK;
            }
        }

        return StatusCodes.FORBIDDEN;
    }

    @Override
    public boolean acceptConfiguration(Object configuration, String endpointUri) throws Exception {
        if (configuration instanceof ElytronSercurityConfiguration) {
            ElytronSercurityConfiguration conf = (ElytronSercurityConfiguration) configuration;
            this.securityDomain = conf.getDomainBuilder().build();
            this.mechanismName = conf.getMechanismName();
            this.elytronProvider = conf.getElytronProvider();
            return true;
        }

        return false;
    }

    /**
     * Elytron hook into undertow is by creation of wrapping httpHandler.
     */
    @Override
    public HttpHandler wrapHttpHandler(HttpHandler httpHandler) throws Exception {
        HttpAuthenticationFactory httpAuthenticationFactory = createHttpAuthenticationFactory(securityDomain);

        HttpHandler rootHandler = new ElytronRunAsHandler(httpHandler);
        rootHandler = new AuthenticationCallHandler(rootHandler);
        rootHandler = new AuthenticationConstraintHandler(rootHandler);

        return ElytronContextAssociationHandler.builder()
                .setNext(rootHandler)
                .setMechanismSupplier(() -> {
                    try {
                        return Collections.singletonList(httpAuthenticationFactory.createMechanism(mechanismName));
                    } catch (HttpAuthenticationException e) {
                        throw new RuntimeCamelException(e);
                    }
                }).build();
    }

    private HttpAuthenticationFactory createHttpAuthenticationFactory(final SecurityDomain securityDomain) {
        HttpServerAuthenticationMechanismFactory providerFactory
                = new SecurityProviderServerMechanismFactory(() -> new Provider[] { this.elytronProvider });
        HttpServerAuthenticationMechanismFactory httpServerMechanismFactory
                = new FilterServerMechanismFactory(providerFactory, true, this.mechanismName);

        return HttpAuthenticationFactory.builder()
                .setSecurityDomain(securityDomain)
                .setMechanismConfigurationSelector(MechanismConfigurationSelector.constantSelector(
                        MechanismConfiguration.builder()
                                .addMechanismRealm(MechanismRealmConfiguration.builder().setRealmName("Elytron Realm").build())
                                .build()))
                .setFactory(httpServerMechanismFactory)
                .build();
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
