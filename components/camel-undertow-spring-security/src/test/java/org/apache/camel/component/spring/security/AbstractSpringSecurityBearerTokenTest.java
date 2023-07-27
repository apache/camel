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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.Filter;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.component.spring.security.keycloak.KeycloakRealmRoleConverter;
import org.apache.camel.component.spring.security.keycloak.KeycloakUsernameSubClaimAdapter;
import org.apache.camel.component.undertow.UndertowComponent;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.security.oauth2.jwt.Jwt;

public abstract class AbstractSpringSecurityBearerTokenTest extends CamelTestSupport {

    private static volatile int port;

    private final MockFilter mockFilter = new MockFilter();

    public MockFilter getMockFilter() {
        return mockFilter;
    }

    @BeforeAll
    public static void initPort() {
        port = AvailablePortFinder.getNextAvailable();
    }

    protected static int getPort() {
        return port;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.getPropertiesComponent().setLocation("ref:prop");

        context.getComponent("undertow", UndertowComponent.class).setSecurityConfiguration(new SpringSecurityConfiguration() {
            @Override
            public Filter getSecurityFilter() {
                return mockFilter;
            }
        });

        return context;
    }

    @BindToRegistry("prop")
    public Properties loadProperties() {

        Properties prop = new Properties();
        prop.setProperty("port", "" + getPort());
        return prop;
    }

    Jwt createToken(String userName, String role) {
        JWTClaimsSet.Builder claimsSet = new JWTClaimsSet.Builder();

        claimsSet.subject("123445667");
        claimsSet.claim("preffered_name", userName);
        claimsSet.audience("resource-server");
        claimsSet.issuer("came-spring-security");

        PlainJWT plainJWT = new PlainJWT(claimsSet.build());

        Map<String, Object> headers = new HashMap<>();
        headers.put("type", "JWT");
        headers.put("alg", "RS256");
        Map<String, Object> claims = new KeycloakUsernameSubClaimAdapter("preffered_name").convert(claimsSet.getClaims());

        JSONArray roles = new JSONArray();
        roles.appendElement(role);
        JSONObject r = new JSONObject();
        r.put(KeycloakRealmRoleConverter.ROLES, roles);
        claims.put(KeycloakRealmRoleConverter.REALM_ACCESS, new JSONObject(r));

        Jwt retVal = new Jwt(plainJWT.serialize(), Instant.now(), Instant.now().plusSeconds(10), headers, claims);
        return retVal;
    }
}
