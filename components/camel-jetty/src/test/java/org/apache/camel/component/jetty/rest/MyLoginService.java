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
package org.apache.camel.component.jetty.rest;

import java.security.Principal;
import java.util.function.Function;

import javax.security.auth.Subject;

import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.UserIdentity;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Session;

public class MyLoginService implements LoginService {

    private IdentityService is = new DefaultIdentityService();

    @Override
    public String getName() {
        return "mylogin";
    }

    @Override
    public UserIdentity login(
            String username, Object credentials, Request request, Function<Boolean, Session> getOrCreateSession) {
        if ("donald".equals(username)) {
            Subject subject = new Subject();
            Principal principal = new Principal() {
                @Override
                public String getName() {
                    return "camel";
                }
            };
            return getIdentityService().newUserIdentity(subject, principal, new String[] { "admin" });
        } else {
            return null;
        }
    }

    @Override
    public boolean validate(UserIdentity userIdentity) {
        return true;
    }

    @Override
    public IdentityService getIdentityService() {
        return is;
    }

    @Override
    public void setIdentityService(IdentityService identityService) {
        this.is = identityService;
    }

    @Override
    public void logout(UserIdentity userIdentity) {
        // noop
    }
}
