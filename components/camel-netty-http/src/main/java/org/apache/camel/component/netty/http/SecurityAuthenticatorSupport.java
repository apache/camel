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
package org.apache.camel.component.netty.http;

import java.security.Principal;
import java.util.Locale;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import org.apache.camel.support.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for {@link SecurityAuthenticator}.
 */
public abstract class SecurityAuthenticatorSupport implements SecurityAuthenticator {

    private String name;
    private String roleClassNames;

    protected SecurityAuthenticatorSupport() {
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setRoleClassNames(String roleClassNames) {
        this.roleClassNames = roleClassNames;
    }

    /**
     * Is the given principal a role class?
     *
     * @param  principal the principal
     * @return           <tt>true</tt> if role class, <tt>false</tt> if not
     */
    protected boolean isRoleClass(Principal principal) {
        if (roleClassNames == null) {
            // by default assume its a role when the classname has role in its name
            return principal.getClass().getName().toLowerCase(Locale.US).contains("role");
        }

        // check each role class name if they match the principal class name
        for (String name : ObjectHelper.createIterable(roleClassNames)) {
            if (principal.getClass().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getUserRoles(Subject subject) {
        StringBuilder sb = new StringBuilder();
        for (Principal p : subject.getPrincipals()) {
            if (isRoleClass(p)) {
                if (!sb.isEmpty()) {
                    sb.append(",");
                }
                sb.append(p.getName());
            }
        }
        if (!sb.isEmpty()) {
            return sb.toString();
        } else {
            return null;
        }
    }

    /**
     * {@link javax.security.auth.callback.CallbackHandler} that provides the username and password.
     */
    public static final class HttpPrincipalCallbackHandler implements CallbackHandler {

        private static final Logger LOG = LoggerFactory.getLogger(HttpPrincipalCallbackHandler.class);

        private final HttpPrincipal principal;

        public HttpPrincipalCallbackHandler(HttpPrincipal principal) {
            this.principal = principal;
        }

        @Override
        public void handle(Callback[] callbacks) {
            for (Callback callback : callbacks) {
                LOG.trace("Callback {}", callback);
                if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;
                    LOG.trace("Setting password on callback {}", pc);
                    pc.setPassword(principal.getPassword().toCharArray());
                } else if (callback instanceof NameCallback) {
                    NameCallback nc = (NameCallback) callback;
                    LOG.trace("Setting username on callback {}", nc);
                    nc.setName(principal.getName());
                }
            }
        }
    }
}
