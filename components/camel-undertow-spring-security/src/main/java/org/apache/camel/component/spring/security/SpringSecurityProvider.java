/**
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
package org.apache.camel.component.spring.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.AttachmentKey;
import io.undertow.util.StatusCodes;
import org.apache.camel.component.undertow.spi.UndertowSecurityProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class SpringSecurityProvider implements UndertowSecurityProvider {

    public static final String PRINCIPAL_NAME_HEADER = SpringSecurityProvider.class.getName() + "_principal";
    private static final AttachmentKey<String> PRINCIPAL_NAME_KEY = AttachmentKey.create(String.class);

    private Filter securityFilter;

    private Map<Undertow, DeploymentManager> deploymenMap = new LinkedHashMap<>();

    @Override
    public void addHeader(BiConsumer<String, Object> consumer, HttpServerExchange httpExchange) throws Exception {
        String principalName = httpExchange.getAttachment(PRINCIPAL_NAME_KEY);
        consumer.accept(PRINCIPAL_NAME_HEADER, principalName);
    }

    @Override
    public int authenticate(HttpServerExchange httpExchange, List<String> allowedRoles) throws Exception {
        ServletRequestContext servletRequestContext = httpExchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletRequest request = servletRequestContext.getServletRequest();
        ServletResponse response = servletRequestContext.getServletResponse();

        //new filter has to be added into the filter chain. If is successfully called it means that security allows access.
        FilterChain fc = (servletRequest, servletResponse) -> {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a instanceof JwtAuthenticationToken) {
                boolean allowed = false;
                Collection<GrantedAuthority> grantedAuthorities = ((JwtAuthenticationToken) a).getAuthorities();
                for (GrantedAuthority grantedAuthority : grantedAuthorities) {
                    if (allowedRoles.contains(grantedAuthority.getAuthority())) {
                        allowed = true;
                        break;
                    }
                }

                if (allowed) {
                    httpExchange.putAttachment(PRINCIPAL_NAME_KEY, ((JwtAuthenticationToken) a).getName());
                    httpExchange.setStatusCode(StatusCodes.OK);
                    return;
                }

                httpExchange.setStatusCode(StatusCodes.FORBIDDEN);
            }
        };
        securityFilter.doFilter(request, response, fc);

        return httpExchange.getStatusCode();
    }


    @Override
    public boolean acceptConfiguration(Object configuration, String endpointUri) throws Exception {
        if (configuration instanceof SpringSecurityConfiguration) {
            SpringSecurityConfiguration conf = (SpringSecurityConfiguration) configuration;
            this.securityFilter = conf.getSecurityFilter();
            return true;
        }

        return false;
    }

    @Override
    public Undertow registerHandler(Undertow.Builder builder, HttpHandler handler) throws Exception {
        DeploymentInfo deployment = Servlets.deployment()
                .setContextPath("")
                .setDisplayName("application")
                .setDeploymentName("camel-undertow")
                .setClassLoader(getClass().getClassLoader())
                //httpHandler for servlet is ignored, camel handler is used instead of it
                .addOuterHandlerChainWrapper(h -> handler);

        DeploymentManager deploymentManager = Servlets.newContainer().addDeployment(deployment);
        deploymentManager.deploy();
        Undertow undertow = UndertowSecurityProvider.super.registerHandler(builder, deploymentManager.start());
        //save into cache for future unregistration
        deploymenMap.put(undertow, deploymentManager);
        return undertow;
    }

    @Override
    public void unregisterHandler(Undertow undertow) {
        if (deploymenMap.containsKey(undertow)) {
            deploymenMap.get(undertow).undeploy();
            deploymenMap.remove(undertow);
        }
    };
}
