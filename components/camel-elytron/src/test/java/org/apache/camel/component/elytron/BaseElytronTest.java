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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.BeforeClass;
import org.wildfly.security.WildFlyElytronBaseProvider;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.realm.token.TokenSecurityRealm;
import org.wildfly.security.auth.realm.token.validator.JwtValidator;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.authz.RoleMapper;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.http.HttpConstants;
import org.wildfly.security.permission.PermissionVerifier;

/**
 * Base class of tests which allocates ports
 */
public abstract class BaseElytronTest extends CamelTestSupport {

    private static volatile int port;
    private static  KeyPair keyPair;

    private final AtomicInteger counter = new AtomicInteger(1);

    abstract String getMechanismName();

    abstract TokenSecurityRealm createBearerRealm() throws NoSuchAlgorithmException;

    abstract WildFlyElytronBaseProvider getElytronProvider();

    @BeforeClass
    public static void initPort() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        keyPair = null;
    }

    protected static int getPort() {
        return port;
    }

    @BindToRegistry("prop")
    public Properties loadProperties() throws Exception {

        Properties prop = new Properties();
        prop.setProperty("port", "" + getPort());
        return prop;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.getPropertiesComponent().setLocation("ref:prop");

        context.getComponent("elytron", ElytronComponent.class).setSecurityDomainBuilder(getSecurityDomainBuilder());
        context.getComponent("elytron", ElytronComponent.class).setMechanismName(getMechanismName());
        context.getComponent("elytron", ElytronComponent.class).setElytronProvider(getElytronProvider());

        return context;
    }

    SecurityDomain.Builder getSecurityDomainBuilder() throws Exception {

        SecurityDomain.Builder builder = SecurityDomain.builder()
                .setDefaultRealmName("realm");

        builder.addRealm("realm", createBearerRealm())
                .build();


        builder.setPermissionMapper((principal, roles) -> PermissionVerifier.from(new LoginPermission()));
        builder.setRoleMapper(RoleMapper.constant(Roles.of("guest")).or(roles -> roles));

        return builder;
    }

    public KeyPair getKeyPair() throws NoSuchAlgorithmException {
        if (keyPair == null) {
            keyPair = generateKeyPair();
        }
        return keyPair;
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

}
