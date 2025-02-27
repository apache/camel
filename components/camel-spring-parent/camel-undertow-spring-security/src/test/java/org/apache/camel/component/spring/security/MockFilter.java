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

import java.io.IOException;
import java.util.Collection;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.apache.camel.component.spring.security.keycloak.KeycloakRealmRoleConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class MockFilter implements Filter {

    private Jwt jwt;

    private boolean putJwtIntoContext = true;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (jwt == null) {
            throw new AccessDeniedException("not allowed");
        }

        Collection<? extends GrantedAuthority> grantedAuthorities = new KeycloakRealmRoleConverter().convert(jwt);

        if (putJwtIntoContext) {
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, grantedAuthorities));
        }

        if (chain != null) {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {

    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public boolean isPutJwtIntoContext() {
        return putJwtIntoContext;
    }

    public void setPutJwtIntoContext(boolean putJwtIntoContext) {
        this.putJwtIntoContext = putJwtIntoContext;
    }
}
