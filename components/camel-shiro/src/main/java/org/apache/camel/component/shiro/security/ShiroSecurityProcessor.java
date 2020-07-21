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
package org.apache.camel.component.shiro.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Processor} that executes the authentication and authorization of the {@link Subject} accordingly
 * to the {@link ShiroSecurityPolicy}.
 */
public class ShiroSecurityProcessor extends DelegateAsyncProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ShiroSecurityProcessor.class);
    private final ShiroSecurityPolicy policy;

    public ShiroSecurityProcessor(Processor processor, ShiroSecurityPolicy policy) {
        super(processor);
        this.policy = policy;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            applySecurityPolicy(exchange);
        } catch (Exception e) {
            // exception occurred so break out
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        return super.process(exchange, callback);
    }

    private void applySecurityPolicy(Exchange exchange) throws Exception {
        ByteSource encryptedToken;

        // if we have username and password as headers then use them to create a token
        String username = exchange.getIn().getHeader(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, String.class);
        String password = exchange.getIn().getHeader(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, String.class);
        if (username != null && password != null) {
            ShiroSecurityToken token = new ShiroSecurityToken(username, password);

            // store the token as header, either as base64 or as the object as-is
            if (policy.isBase64()) {
                ByteSource bytes = ShiroSecurityHelper.encrypt(token, policy.getPassPhrase(), policy.getCipherService());
                String base64 = bytes.toBase64();
                exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, base64);
            } else {
                exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, token);
            }
            // and now remove the headers as we turned those into the token instead
            exchange.getIn().removeHeader(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME);
            exchange.getIn().removeHeader(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD);
        }

        Object token = ExchangeHelper.getMandatoryHeader(exchange, ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, Object.class);

        // we support the token in a number of ways
        if (token instanceof ShiroSecurityToken) {
            ShiroSecurityToken sst = (ShiroSecurityToken) token;
            encryptedToken = ShiroSecurityHelper.encrypt(sst, policy.getPassPhrase(), policy.getCipherService());
            // Remove unencrypted token + replace with an encrypted token
            exchange.getIn().removeHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN);
            exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, encryptedToken);
        } else if (token instanceof String) {
            String data = (String) token;
            if (policy.isBase64()) {
                byte[] bytes = Base64.decode(data);
                encryptedToken = ByteSource.Util.bytes(bytes);
            } else {
                encryptedToken = ByteSource.Util.bytes(data);
            }
        } else if (token instanceof ByteSource) {
            encryptedToken = (ByteSource) token;
        } else {
            throw new CamelExchangeException("Shiro security header " + ShiroSecurityConstants.SHIRO_SECURITY_TOKEN + " is unsupported type: " + ObjectHelper.classCanonicalName(token), exchange);
        }

        ByteSource decryptedToken = policy.getCipherService().decrypt(encryptedToken.getBytes(), policy.getPassPhrase());

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(decryptedToken.getBytes());
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                if (!(desc.getName().equals(ShiroSecurityToken.class.getName())
                    || "java.lang.String".equals(desc.getName()))) {
                    throw new InvalidClassException("Unauthorized deserialization attempt", desc.getName());
                }
                return super.resolveClass(desc);
            }

        };
        ShiroSecurityToken securityToken;
        try {
            securityToken = (ShiroSecurityToken)objectInputStream.readObject();
        } finally {
            IOHelper.close(objectInputStream, byteArrayInputStream);
        }

        Subject currentUser = SecurityUtils.getSubject();

        // Authenticate user if not authenticated
        try {
            authenticateUser(currentUser, securityToken);

            // Test whether user's role is authorized to perform functions in the permissions list
            authorizeUser(currentUser, exchange);
        } finally {
            if (policy.isAlwaysReauthenticate()) {
                currentUser.logout();
            }
        }
    }

    private void authenticateUser(Subject currentUser, ShiroSecurityToken securityToken) {
        boolean authenticated = currentUser.isAuthenticated();
        boolean sameUser = securityToken.getUsername().equals(currentUser.getPrincipal());
        LOG.trace("Authenticated: {}, same Username: {}", authenticated, sameUser);

        if (!authenticated || !sameUser) {
            UsernamePasswordToken token = new UsernamePasswordToken(securityToken.getUsername(), securityToken.getPassword());
            if (policy.isAlwaysReauthenticate()) {
                token.setRememberMe(false);
            } else {
                token.setRememberMe(true);
            }

            try {
                currentUser.login(token);
                LOG.debug("Current user {} successfully authenticated", currentUser.getPrincipal());
            } catch (UnknownAccountException uae) {
                throw new UnknownAccountException("Authentication Failed. There is no user with username of " + token.getPrincipal(), uae.getCause());
            } catch (IncorrectCredentialsException ice) {
                throw new IncorrectCredentialsException("Authentication Failed. Password for account " + token.getPrincipal() + " was incorrect!", ice.getCause());
            } catch (LockedAccountException lae) {
                throw new LockedAccountException("Authentication Failed. The account for username " + token.getPrincipal() + " is locked."
                        + " Please contact your administrator to unlock it.", lae.getCause());
            } catch (AuthenticationException ae) {
                throw new AuthenticationException("Authentication Failed.", ae.getCause());
            }
        }
    }

    private void authorizeUser(Subject currentUser, Exchange exchange) throws CamelAuthorizationException {
        boolean authorized = false;
        if (!policy.getPermissionsList().isEmpty()) {
            if (policy.isAllPermissionsRequired()) {
                authorized = currentUser.isPermittedAll(policy.getPermissionsList());
            } else {
                for (Permission permission : policy.getPermissionsList()) {
                    if (currentUser.isPermitted(permission)) {
                        authorized = true;
                        break;
                    }
                }
            }
        } else if (!policy.getRolesList().isEmpty()) {
            if (policy.isAllRolesRequired()) {
                authorized = currentUser.hasAllRoles(policy.getRolesList());
            } else {
                for (String role : policy.getRolesList()) {
                    if (currentUser.hasRole(role)) {
                        authorized = true;
                        break;
                    }
                }
            }
        } else {
            LOG.trace("Valid Permissions or Roles List not specified for ShiroSecurityPolicy. "
                      + "No authorization checks will be performed for current user.");
            authorized = true;
        }

        if (!authorized) {
            throw new CamelAuthorizationException("Authorization Failed. Subject's role set does "
                                                  + "not have the necessary roles or permissions to perform further processing.", exchange);
        }

        LOG.debug("Current user {} is successfully authorized.", currentUser.getPrincipal());
    }

}
