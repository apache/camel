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
package org.apache.camel.component.spring.security;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import io.undertow.server.HttpServerExchange;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.util.AttachmentKey;
import io.undertow.util.StatusCodes;
import org.apache.camel.component.undertow.spi.UndertowSecurityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class SpringSecurityProvider implements UndertowSecurityProvider {
    public static final String PRINCIPAL_NAME_HEADER = SpringSecurityProvider.class.getName() + "_principal";

    private static final Logger LOG = LoggerFactory.getLogger(SpringSecurityProvider.class);
    private static final AttachmentKey<String> PRINCIPAL_NAME_KEY = AttachmentKey.create(String.class);

    private Filter securityFilter;

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
                LOG.debug("Authentication token is present.");
                boolean allowed = false;
                Collection<GrantedAuthority> grantedAuthorities = ((JwtAuthenticationToken) a).getAuthorities();
                for (GrantedAuthority grantedAuthority : grantedAuthorities) {
                    if (allowedRoles.contains(grantedAuthority.getAuthority())) {
                        LOG.debug("Authenticated principal {} has authority to access resource.",
                                ((JwtAuthenticationToken) a).getName());
                        allowed = true;
                        break;
                    }
                }

                if (allowed) {
                    httpExchange.putAttachment(PRINCIPAL_NAME_KEY, ((JwtAuthenticationToken) a).getName());
                    httpExchange.setStatusCode(StatusCodes.OK);
                    return;
                } else {
                    LOG.debug("Authenticated principal {} doesn't have authority to access resource.",
                            ((JwtAuthenticationToken) a).getName());
                }

            } else {
                //this is logged as warn, because it shows an error in configuration
                //spring-security shouldn't allow to access this code if configuration is correct
                LOG.warn("Authentication token is not present. Access is FORBIDDEN.");
            }
            httpExchange.setStatusCode(StatusCodes.FORBIDDEN);
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
    public boolean requireServletContext() {
        return true;
    }
}
