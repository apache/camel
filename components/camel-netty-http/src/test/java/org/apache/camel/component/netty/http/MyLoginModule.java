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
package org.apache.camel.component.netty.http;

import java.io.IOException;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

public class MyLoginModule implements LoginModule {

    private Subject subject;
    private CallbackHandler callbackHandler;

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public boolean login() throws LoginException {

        // get username and password
        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("username");
        callbacks[1] = new PasswordCallback("password", false);

        try {
            callbackHandler.handle(callbacks);
            String username = ((NameCallback)callbacks[0]).getName();
            char[] tmpPassword = ((PasswordCallback)callbacks[1]).getPassword();
            String password = new String(tmpPassword);
            ((PasswordCallback)callbacks[1]).clearPassword();

            // only allow login if password is secret
            // as this is just for testing purpose
            if (!"secret".equals(password)) {
                throw new LoginException("Login denied");
            }

            // add roles
            if ("scott".equals(username)) {
                subject.getPrincipals().add(new MyRolePrincipal("admin"));
                subject.getPrincipals().add(new MyRolePrincipal("guest"));
            } else if ("guest".equals(username)) {
                subject.getPrincipals().add(new MyRolePrincipal("guest"));
            }

        } catch (IOException ioe) {
            LoginException le = new LoginException(ioe.toString());
            le.initCause(ioe);
            throw le;
        } catch (UnsupportedCallbackException uce) {
            LoginException le = new LoginException("Error: " + uce.getCallback().toString()
                    + " not available to gather authentication information from the user");
            le.initCause(uce);
            throw le;
        }


        return true;
    }

    @Override
    public boolean commit() throws LoginException {
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        subject = null;
        callbackHandler = null;
        return true;
    }

}
